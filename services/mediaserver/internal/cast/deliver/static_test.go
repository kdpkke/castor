package deliver

import (
	"context"
	"io"
	"net"
	"net/http"
	"testing"
)

type loopback struct{}

func (loopback) Listen(context.Context) (net.Listener, error) {
	return net.Listen("tcp", "127.0.0.1:0")
}

func TestStaticServesTheDocumentToAnyOrigin(t *testing.T) {
	u, closeStatic, err := Static(t.Context(), loopback{}, "en.vtt", "text/vtt", "WEBVTT\n\n")
	if err != nil {
		t.Fatal(err)
	}
	defer closeStatic()
	bare := *u
	bare.Path = "/en.vtt"
	refused, err := http.Get(bare.String())
	if err != nil {
		t.Fatal(err)
	}
	refused.Body.Close()
	if refused.StatusCode != http.StatusNotFound {
		t.Fatalf("caption without capability: %d", refused.StatusCode)
	}
	resp, err := http.Get(u.String())
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if string(body) != "WEBVTT\n\n" || resp.Header.Get("Content-Type") != "text/vtt" || resp.Header.Get("Access-Control-Allow-Origin") != "*" {
		t.Errorf("served %q as %q with origin %q", body, resp.Header.Get("Content-Type"), resp.Header.Get("Access-Control-Allow-Origin"))
	}
}
