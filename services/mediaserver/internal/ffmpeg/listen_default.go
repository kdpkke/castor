//go:build !android

package ffmpeg

import (
	"net"
	"net/url"
)

func listenSideOutput() (net.Listener, string, func(), error) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, "", nil, err
	}
	address := (&url.URL{Scheme: "tcp", Host: ln.Addr().String(), RawQuery: "tcp_nodelay=1"}).String()
	return ln, address, func() { _ = ln.Close() }, nil
}
