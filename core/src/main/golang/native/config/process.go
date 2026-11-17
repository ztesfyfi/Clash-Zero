package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/dlclark/regexp2"

	"cfa/native/common"

	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
)

var processors = []processor{
	patchExternalController, // must before patchOverride, so we only apply ExternalController in Override settings
	patchOverride,
	patchGeneral,
	patchProfile,
	patchDns,
	patchTun,
	patchListeners,
	patchStrategy,
	patchProviders,
	validConfig,
}

type processor func(cfg *config.RawConfig, profileDir string) error

func patchOverride(cfg *config.RawConfig, _ string) error {
	if err := json.NewDecoder(strings.NewReader(ReadOverride(OverrideSlotPersist))).Decode(cfg); err != nil {
		log.Warnln("Apply persist override: %s", err.Error())
	}
	if err := json.NewDecoder(strings.NewReader(ReadOverride(OverrideSlotSession))).Decode(cfg); err != nil {
		log.Warnln("Apply session override: %s", err.Error())
	}

	return nil
}

func patchExternalController(cfg *config.RawConfig, _ string) error {
	cfg.ExternalController = ""
	cfg.ExternalControllerTLS = ""

	return nil
}

func patchGeneral(cfg *config.RawConfig, profileDir string) error {
	cfg.Interface = ""
	cfg.RoutingMark = 0
	if cfg.ExternalController != "" || cfg.ExternalControllerTLS != "" {
		cfg.ExternalUI = profileDir + "/ui"
	}

	return nil
}

func patchProfile(cfg *config.RawConfig, _ string) error {
	cfg.Profile.StoreSelected = false
	cfg.Profile.StoreFakeIP = true

	return nil
}

func patchDns(cfg *config.RawConfig, _ string) error {
	if !cfg.DNS.Enable {
		cfg.DNS = config.DefaultRawConfig().DNS
		cfg.DNS.Enable = true
		cfg.DNS.NameServer = defaultNameServers
		cfg.DNS.EnhancedMode = C.DNSFakeIP
		cfg.DNS.FakeIPRange = defaultFakeIPRange
		cfg.DNS.FakeIPFilter = defaultFakeIPFilter

		cfg.ClashForAndroid.AppendSystemDNS = true
	}

	if cfg.ClashForAndroid.AppendSystemDNS {
		cfg.DNS.NameServer = append(cfg.DNS.NameServer, "system://")
	}

	return nil
}

func patchTun(cfg *config.RawConfig, _ string) error {
	cfg.Tun.Enable = false
	cfg.Tun.AutoRoute = false
	cfg.Tun.AutoDetectInterface = false
	return nil
}

func patchListeners(cfg *config.RawConfig, _ string) error {
	newListeners := make([]map[string]any, 0, len(cfg.Listeners))
	for _, mapping := range cfg.Listeners {
		if proxyType, existType := mapping["type"].(string); existType {
			switch proxyType {
			case "tproxy", "redir", "tun":
				continue // remove those listeners which is not supported
			}
		}
		newListeners = append(newListeners, mapping)
	}
	cfg.Listeners = newListeners
	return nil
}

func patchProviders(cfg *config.RawConfig, profileDir string) error {
	forEachProviders(cfg, func(index int, total int, key string, provider map[string]any, prefix string) {
		path, _ := provider["path"].(string)
		if len(path) > 0 {
			path = common.ResolveAsRoot(path)
		} else if url, ok := provider["url"].(string); ok {
			path = prefix + "/" + utils.MakeHash([]byte(url)).String() // same as C.GetPathByHash
		} else {
			return // both path and url are empty, maybe inline provider
		}
		provider["path"] = profileDir + "/providers/" + path
	})

	return nil
}


func patchStrategy(cfg *config.RawConfig, profileDir string) error {
	strategyPath := filepath.Join(profileDir, "strategy.json")
	data, err := os.ReadFile(strategyPath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return fmt.Errorf("read generated strategy overlay: %w", err)
	}

	var overlay struct {
		ProxyGroups   []map[string]any           `json:"proxy-groups"`
		RuleProviders map[string]map[string]any  `json:"rule-providers"`
		Rules         []string                   `json:"rules"`
		ExternalProxies []struct {
			ProfileUUID string `json:"profileUuid"`
			Key         string `json:"key"`
			Name        string `json:"name"`
		} `json:"external-proxies"`
	}
	if err := json.Unmarshal(data, &overlay); err != nil {
		return fmt.Errorf("decode generated strategy overlay: %w", err)
	}

	// Import only explicitly selected nodes from other subscriptions. The
	// stable key is calculated from each source profile's normalized proxy
	// definition, so display-name changes do not silently select another node.
	if len(overlay.ExternalProxies) > 0 {
		requests := make(map[string][]string)
		aliases := make(map[string]string)
		existingNames := make(map[string]struct{}, len(cfg.Proxy))
		for _, proxy := range cfg.Proxy {
			if name, ok := proxy["name"].(string); ok && name != "" {
				existingNames[name] = struct{}{}
			}
		}
		for _, item := range overlay.ExternalProxies {
			if len(item.ProfileUUID) != 36 || item.Key == "" || item.Name == "" {
				return fmt.Errorf("invalid external proxy reference")
			}
			if _, exists := existingNames[item.Name]; exists {
				return fmt.Errorf("external proxy name %q conflicts with an existing proxy", item.Name)
			}
			if _, exists := aliases[item.Name]; exists {
				return fmt.Errorf("duplicate external proxy alias %q", item.Name)
			}
			aliases[item.Name] = item.ProfileUUID + ":" + item.Key
			requests[item.ProfileUUID] = append(requests[item.ProfileUUID], item.Key)
		}
		for profileUUID, keys := range requests {
			sourceDir := filepath.Join(filepath.Dir(profileDir), profileUUID)
			definitions, err := QueryProfileProxyDefinitions(sourceDir, keys)
			if err != nil {
				return fmt.Errorf("load selected nodes from subscription %s: %w", profileUUID, err)
			}
			for _, item := range overlay.ExternalProxies {
				if item.ProfileUUID != profileUUID {
					continue
				}
				proxy, ok := definitions[item.Key]
				if !ok {
					return fmt.Errorf("selected proxy %q is no longer available in subscription %s", item.Name, profileUUID)
				}
				proxy = cloneProxy(proxy)
				proxy["name"] = item.Name
				cfg.Proxy = append(cfg.Proxy, proxy)
			}
		}
	}

	cfg.ProxyGroup = append(cfg.ProxyGroup, overlay.ProxyGroups...)
	if len(overlay.RuleProviders) > 0 {
		if cfg.RuleProvider == nil {
			cfg.RuleProvider = make(map[string]map[string]any)
		}
		for name, provider := range overlay.RuleProviders {
			if _, exists := cfg.RuleProvider[name]; exists {
				return fmt.Errorf("generated rule-provider %q conflicts with profile provider", name)
			}
			cfg.RuleProvider[name] = provider
		}
	}
	if len(overlay.Rules) > 0 {
		// User-defined rules have explicit priorities and must precede base
		// profile rules, including a terminal MATCH rule.
		cfg.Rule = append(append([]string{}, overlay.Rules...), cfg.Rule...)
	}
	return nil
}

func validConfig(cfg *config.RawConfig, _ string) error {
	if len(cfg.Proxy) == 0 && len(cfg.ProxyProvider) == 0 {
		return errors.New("profile does not contain `proxies` or `proxy-providers`")
	}

	if _, err := regexp2.Compile(cfg.ClashForAndroid.UiSubtitlePattern, 0); err != nil {
		return fmt.Errorf("compile ui-subtitle-pattern: %s", err.Error())
	}

	return nil
}

func process(cfg *config.RawConfig, profileDir string) error {
	for _, p := range processors {
		if err := p(cfg, profileDir); err != nil {
			return err
		}
	}

	return nil
}
