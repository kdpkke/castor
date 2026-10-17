package mediaserver

import (
	"context"
	"log/slog"

	"github.com/stupside/castor/services/mediaserver/internal/media"
	"github.com/stupside/castor/services/mediaserver/internal/probe"
	"github.com/stupside/castor/services/mediaserver/internal/source"
)

// candidateProbe first measures the origin directly. HLS without a usable
// picture gets one measurement through the segment reader playback uses.
type candidateProbe struct {
	engine    *engine
	stream    *source.Stream
	maxHeight media.HeightCap
}

func (p candidateProbe) Probe(ctx context.Context) (media.ProbeInfo, media.Reach, error) {
	e, s := p.engine, p.stream
	if contentType := formats.ContentTypeOf(s.URL, s.ContentType); contentType != "" {
		s.ContentType = contentType
	}
	link := probe.Link{URL: s.URL, Headers: s.Headers, InputArgs: formats.InputArgs(s.ContentType, 0)}
	info, reach, err := e.machinery.Probes.Link(link, fetchTimeout).Probe(ctx)
	if err != nil || (info.VideoCodec != "png" && info.VideoCodec != "") || s.ContentType != media.HLS {
		return info, reach, err
	}
	slog.InfoContext(ctx, "measuring HLS without a usable picture through the segment reader", "host", s.URL.Host)
	resolved, err := source.NewResolver(e.client, p.maxHeight, formats).Resolve(ctx, s, source.Rendition{})
	if err != nil {
		return media.ProbeInfo{}, reach, err
	}
	program, stop, err := e.machinery.Timelines.Republish(ctx, resolved.Program)
	if err != nil {
		return media.ProbeInfo{}, reach, err
	}
	defer stop()
	input, ok := program.PrimaryInput()
	if !ok {
		return info, reach, nil
	}
	return e.machinery.Probes.Link(probe.Link{
		URL: input.URL, Headers: input.Headers, InputArgs: formats.InputArgs(input.ContentType, 0),
	}, fetchTimeout).Probe(ctx)
}
