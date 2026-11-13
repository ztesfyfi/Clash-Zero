package config

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path"

	"github.com/metacubex/mihomo/config"
)

type ProfileProxy struct {
	Key  string `json:"key"`
	Name string `json:"name"`
	Type string `json:"type"`
}

// QueryProfileProxies reads the raw configuration belonging to one profile.
// It deliberately does not query Mihomo's active global proxy registry.
func QueryProfileProxies(profilePath string) ([]ProfileProxy, error) {
	data, err := os.ReadFile(path.Join(profilePath, "config.yaml"))
	if err != nil {
		return nil, fmt.Errorf("read profile config: %w", err)
	}

	raw, err := config.UnmarshalRawConfig(data)
	if err != nil {
		return nil, fmt.Errorf("parse profile config: %w", err)
	}

	result := make([]ProfileProxy, 0, len(raw.Proxy))
	for _, proxy := range raw.Proxy {
		name, _ := proxy["name"].(string)
		proxyType, _ := proxy["type"].(string)
		if name == "" || proxyType == "" {
			continue
		}

		// Exclude the mutable display name so a provider-side rename does not
		// invalidate the local node identity. JSON marshaling sorts map keys.
		identity := make(map[string]any, len(proxy))
		for key, value := range proxy {
			if key != "name" {
				identity[key] = value
			}
		}
		encoded, err := json.Marshal(identity)
		if err != nil {
			return nil, fmt.Errorf("encode proxy %q identity: %w", name, err)
		}
		sum := sha256.Sum256(encoded)
		result = append(result, ProfileProxy{
			Key:  hex.EncodeToString(sum[:]),
			Name: name,
			Type: proxyType,
		})
	}

	return result, nil
}
