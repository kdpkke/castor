package transport

import (
	"crypto/rand"
	"net/http"
)

// SecretPath lends h an unguessable URL path when a client cannot carry bearer headers.
func SecretPath(h http.Handler) (string, http.Handler) {
	prefix := "/" + rand.Text()
	mux := http.NewServeMux()
	mux.Handle(prefix+"/", http.StripPrefix(prefix, h))
	return prefix, mux
}
