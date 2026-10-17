package device

import (
	"context"
	"testing"
	"time"

	castorv1 "github.com/stupside/castor/gen/castor/v1"
)

func TestPlatformDiscoveryUsesNativeIdentityAndKeepsTheDirectoryContract(t *testing.T) {
	find := PlatformDiscovery(func(context.Context) ([]*castorv1.Device, error) {
		living := &castorv1.Device{Id: "native-tv", Name: "Living room", Type: castorv1.DeviceType_DEVICE_TYPE_DLNA, Address: "http://192.0.2.20/description.xml"}
		return []*castorv1.Device{living, living, {Id: "unknown", Type: castorv1.DeviceType(99)}, nil}, nil
	})
	r := Registry{Families: []Family{stub{}}, Discovery: find, Timeout: time.Second}
	d := NewDirectory(r)
	listed, err := d.ListDevices(t.Context(), &castorv1.ListDevicesRequest{})
	if err != nil || len(listed.GetDevices()) != 1 {
		t.Fatalf("ListDevices = %v, %v, want one native device", listed, err)
	}
	device := listed.GetDevices()[0]
	if device.GetId() != "dlna:native-tv" || device.GetName() != "Living room" {
		t.Fatalf("native identity changed: %v", device)
	}
	info, err := d.Target(t.Context(), &castorv1.Target{Target: &castorv1.Target_DeviceId{DeviceId: device.GetId()}})
	if err != nil || info.Address != "http://192.0.2.20/description.xml" {
		t.Fatalf("native target = %+v, %v", info, err)
	}
}
