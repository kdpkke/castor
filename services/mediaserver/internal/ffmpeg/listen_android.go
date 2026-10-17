package ffmpeg

import (
	"net"
	"os"
	"path/filepath"
)

// The app-private directory prevents other Android UIDs from claiming a side channel.
func listenSideOutput() (net.Listener, string, func(), error) {
	dir, err := os.MkdirTemp("", "castor-side-")
	if err != nil {
		return nil, "", nil, err
	}
	path := filepath.Join(dir, "s")
	ln, err := net.Listen("unix", path)
	if err != nil {
		_ = os.Remove(dir)
		return nil, "", nil, err
	}
	return ln, "unix://" + path, func() { _ = ln.Close(); _ = os.Remove(dir) }, nil
}
