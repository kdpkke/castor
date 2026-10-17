package transport

import (
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestPrivateMediaRequiresItsCapabilityPath(t *testing.T) {
	called := 0
	prefix, handler := SecretPath(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		called++
		if r.URL.Path != "/stream.ts" || r.URL.RawQuery != "attempt=2" {
			t.Errorf("resource changed at the boundary: %s", r.URL)
		}
		_, _ = io.WriteString(w, "video")
	}))
	for _, path := range []string{"/stream.ts?attempt=2", "/other/stream.ts?attempt=2", prefix + "wrong/stream.ts?attempt=2"} {
		response := httptest.NewRecorder()
		handler.ServeHTTP(response, httptest.NewRequest(http.MethodGet, path, nil))
		if response.Code != http.StatusNotFound || called != 0 {
			t.Fatalf("an unknown capability reached private media: %s, %d", path, response.Code)
		}
	}
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, httptest.NewRequest(http.MethodGet, prefix+"/stream.ts?attempt=2", nil))
	if response.Code != http.StatusOK || response.Body.String() != "video" || called != 1 {
		t.Fatalf("the capability did not reach its resource: %d %q, calls=%d", response.Code, response.Body.String(), called)
	}
}
