package device

import (
	"context"
	"log/slog"

	castorv1 "github.com/stupside/castor/gen/castor/v1"
)

// PlatformDiscovery adapts native discoveries, keeping the directory and device protocols in the API.
func PlatformDiscovery(find func(context.Context) ([]*castorv1.Device, error)) func(context.Context) []Info {
	return func(ctx context.Context) []Info {
		found, err := find(ctx)
		if err != nil {
			slog.WarnContext(ctx, "platform discovery failed", "error", err)
			return nil
		}
		var out []Info
		seen := map[string]bool{}
		for _, d := range found {
			kind, err := requestedType(d.GetType())
			if err != nil || d.GetId() == "" || d.GetAddress() == "" {
				slog.WarnContext(ctx, "platform returned an invalid device", "id", d.GetId())
				continue
			}
			info := Info{ID: d.GetId(), Name: d.GetName(), Type: kind, Address: d.GetAddress()}
			if !seen[info.id()] {
				seen[info.id()] = true
				out = append(out, info)
			}
		}
		return out
	}
}
