package config

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"sort"

	"github.com/metacubex/mihomo/common/yaml"
)

type ProfileProxy struct {
	Key  string `json:"key"`
	Name string `json:"name"`
	Type string `json:"type"`
}

type proxyProviderFile struct {
	Proxies []map[string]any `yaml:"proxies"`
}

func QueryProfileProxies(profilePath string) ([]ProfileProxy, error) {
	raw, err := UnmarshalAndPatch(profilePath)
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
			if err := appendProxy(proxy); err != nil {
				return nil, err
			}
		}
	}

	return result, nil
}
