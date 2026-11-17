package config

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"regexp"

	"github.com/metacubex/mihomo/common/yaml"
	mihomoConfig "github.com/metacubex/mihomo/config"
)

type ProfileProxy struct {
	Key  string `json:"key"`
	Name string `json:"name"`
	Type string `json:"type"`
}

func cloneProxy(proxy map[string]any) map[string]any {
	cloned := make(map[string]any, len(proxy))
	for key, value := range proxy {
		cloned[key] = value
	}
	return cloned
}

func providerProxyName(name string, provider map[string]any) string {
	override, _ := provider["override"].(map[string]any)
	rules, _ := override["proxy-name"].([]any)
	for _, rawRule := range rules {
		rule, ok := rawRule.(map[string]any)
		if !ok {
			continue
		}
		pattern, _ := rule["pattern"].(string)
		target, _ := rule["target"].(string)
		if pattern == "" {
			continue
		}
		expression, err := regexp.Compile(pattern)
		if err == nil {
			name = expression.ReplaceAllString(name, target)
		}
	}
	prefix, _ := override["additional-prefix"].(string)
	suffix, _ := override["additional-suffix"].(string)
	return prefix + name + suffix
}

type proxyProviderFile struct {
	Proxies []map[string]any `yaml:"proxies"`
}

func unmarshalProfileProxyConfig(profilePath string) (*mihomoConfig.RawConfig, error) {
	data, err := os.ReadFile(filepath.Join(profilePath, "config.yaml"))
	if err != nil {
		return nil, err
	}
	raw, err := mihomoConfig.UnmarshalRawConfig(data)
	if err != nil {
		return nil, err
	}
	if err := patchProviders(raw, profilePath); err != nil {
		return nil, err
	}
	return raw, nil
}

func QueryProfileProxies(profilePath string) ([]ProfileProxy, error) {
	raw, err := unmarshalProfileProxyConfig(profilePath)
	if err != nil {
		return nil, fmt.Errorf("parse profile config: %w", err)
	}

	result := make([]ProfileProxy, 0, len(raw.Proxy))
	seen := make(map[string]int)
	appendProxy := func(proxy map[string]any) error {
		name, _ := proxy["name"].(string)
		proxyType, _ := proxy["type"].(string)
		if name == "" || proxyType == "" {
			return nil
		}

		identity := make(map[string]any, len(proxy))
		for key, value := range proxy {
			if key != "name" {
				identity[key] = value
			}
		}
		encoded, err := json.Marshal(identity)
		if err != nil {
			return fmt.Errorf("encode proxy %q identity: %w", name, err)
		}
		sum := sha256.Sum256(encoded)
		baseKey := hex.EncodeToString(sum[:])
		seen[baseKey]++
		key := baseKey
		if seen[baseKey] > 1 {
			key = fmt.Sprintf("%s-%d", baseKey, seen[baseKey])
		}
		result = append(result, ProfileProxy{Key: key, Name: name, Type: proxyType})
		return nil
	}

	for _, proxy := range raw.Proxy {
		if err := appendProxy(proxy); err != nil {
			return nil, err
		}
	}

	// Provider files are stored beneath this profile's providers directory by
	// patchProviders. Parse them locally so subscriptions with proxy-providers
	// expose their real node entries without consulting the active global core.
	providerNames := make([]string, 0, len(raw.ProxyProvider))
	for providerName := range raw.ProxyProvider {
		providerNames = append(providerNames, providerName)
	}
	sort.Strings(providerNames)
	for _, providerName := range providerNames {
		provider := raw.ProxyProvider[providerName]
		providerPath, _ := provider["path"].(string)
		if providerPath == "" {
			if payload, ok := provider["payload"].([]any); ok {
				for _, item := range payload {
					if proxy, ok := item.(map[string]any); ok {
						proxy = cloneProxy(proxy)
						name, _ := proxy["name"].(string)
						proxy["name"] = providerProxyName(name, provider)
						if err := appendProxy(proxy); err != nil {
							return nil, err
						}
					}
				}
			}
			continue
		}
		data, err := os.ReadFile(providerPath)
		if err != nil {
			return nil, fmt.Errorf("read proxy provider %q: %w", providerName, err)
		}
		var file proxyProviderFile
		if err := yaml.Unmarshal(data, &file); err != nil {
			return nil, fmt.Errorf("parse proxy provider %q: %w", providerName, err)
		}
		for _, proxy := range file.Proxies {
			proxy = cloneProxy(proxy)
			name, _ := proxy["name"].(string)
			proxy["name"] = providerProxyName(name, provider)
			if err := appendProxy(proxy); err != nil {
				return nil, err
			}
		}
	}

	return result, nil
}


// QueryProfileProxyDefinitions returns the raw Mihomo proxy definitions for
// stable profile proxy keys. It is used to import selected nodes from another
// subscription into the active profile's generated configuration.
func QueryProfileProxyDefinitions(profilePath string, requestedKeys []string) (map[string]map[string]any, error) {
	raw, err := unmarshalProfileProxyConfig(profilePath)
	if err != nil {
		return nil, fmt.Errorf("parse source profile config: %w", err)
	}
	wanted := make(map[string]struct{}, len(requestedKeys))
	for _, key := range requestedKeys {
		wanted[key] = struct{}{}
	}
	result := make(map[string]map[string]any, len(wanted))
	seen := make(map[string]int)
	appendProxy := func(proxy map[string]any) error {
		name, _ := proxy["name"].(string)
		proxyType, _ := proxy["type"].(string)
		if name == "" || proxyType == "" {
			return nil
		}
		identity := make(map[string]any, len(proxy))
		for key, value := range proxy {
			if key != "name" {
				identity[key] = value
			}
		}
		encoded, err := json.Marshal(identity)
		if err != nil {
			return fmt.Errorf("encode proxy %q identity: %w", name, err)
		}
		sum := sha256.Sum256(encoded)
		baseKey := hex.EncodeToString(sum[:])
		seen[baseKey]++
		key := baseKey
		if seen[baseKey] > 1 {
			key = fmt.Sprintf("%s-%d", baseKey, seen[baseKey])
		}
		if _, ok := wanted[key]; ok {
			result[key] = cloneProxy(proxy)
		}
		return nil
	}
	for _, proxy := range raw.Proxy {
		if err := appendProxy(proxy); err != nil {
			return nil, err
		}
	}
	providerNames := make([]string, 0, len(raw.ProxyProvider))
	for providerName := range raw.ProxyProvider {
		providerNames = append(providerNames, providerName)
	}
	sort.Strings(providerNames)
	for _, providerName := range providerNames {
		provider := raw.ProxyProvider[providerName]
		providerPath, _ := provider["path"].(string)
		if providerPath == "" {
			if payload, ok := provider["payload"].([]any); ok {
				for _, item := range payload {
					if proxy, ok := item.(map[string]any); ok {
						proxy = cloneProxy(proxy)
						name, _ := proxy["name"].(string)
						proxy["name"] = providerProxyName(name, provider)
						if err := appendProxy(proxy); err != nil {
							return nil, err
						}
					}
				}
			}
			continue
		}
		data, err := os.ReadFile(providerPath)
		if err != nil {
			return nil, fmt.Errorf("read proxy provider %q: %w", providerName, err)
		}
		var file proxyProviderFile
		if err := yaml.Unmarshal(data, &file); err != nil {
			return nil, fmt.Errorf("parse proxy provider %q: %w", providerName, err)
		}
		for _, proxy := range file.Proxies {
			proxy = cloneProxy(proxy)
			name, _ := proxy["name"].(string)
			proxy["name"] = providerProxyName(name, provider)
			if err := appendProxy(proxy); err != nil {
				return nil, err
			}
		}
	}
	if len(result) != len(wanted) {
		missing := make([]string, 0)
		for key := range wanted {
			if _, ok := result[key]; !ok {
				missing = append(missing, key)
			}
		}
		sort.Strings(missing)
		return nil, fmt.Errorf("source profile is missing selected proxy keys: %s", strings.Join(missing, ", "))
	}
	return result, nil
}
