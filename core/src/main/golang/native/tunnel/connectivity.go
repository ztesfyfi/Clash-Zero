package tunnel

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	mihomoTunnel "github.com/metacubex/mihomo/tunnel"
)

func HealthCheck(name string) {
	p := mihomoTunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Request health check for `%s`: not found", name)

		return
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Request health check for `%s`: invalid type %s", name, p.Type().String())

		return
	}

	wg := &sync.WaitGroup{}

	for _, pr := range g.Providers() {
		wg.Add(1)

		go func(provider provider.ProxyProvider) {
			provider.HealthCheck()

			wg.Done()
		}(pr)
	}

	wg.Wait()
}

func HealthCheckAll() {
	for _, g := range QueryProxyGroupNames(false) {
		go func(group string) {
			HealthCheck(group)
		}(g)
	}
}

func TestProxy(name, url string, timeout time.Duration) (int, error) {
	proxy := mihomoTunnel.Proxies()[name]
	if proxy == nil {
		// Provider nodes are held by proxy providers rather than the top-level
		// proxy map. Search the active profile's groups so provider-backed nodes
		// can be tested individually as well.
		for _, groupProxy := range mihomoTunnel.Proxies() {
			group, ok := groupProxy.Adapter().(outboundgroup.ProxyGroup)
			if !ok {
				continue
			}
			for _, proxyProvider := range group.Providers() {
				for _, candidate := range proxyProvider.Proxies() {
					if candidate.Name() == name {
						proxy = candidate
						break
					}
				}
				if proxy != nil {
					break
				}
			}
			if proxy != nil {
				break
			}
		}
	}
	if proxy == nil {
		return 0, fmt.Errorf("proxy %q is not present in the active profile", name)
	}

	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	delay, err := proxy.URLTest(ctx, url, nil)
	if err != nil {
		return 0, err
	}
	return int(delay), nil
}
