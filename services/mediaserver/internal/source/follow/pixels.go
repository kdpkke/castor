package follow

import (
	"bytes"
	"compress/gzip"
	"encoding/binary"
	"fmt"
	"image/color"
	"image/png"
	"io"
)

const pixelLimit = 32 << 20

// unwrapPixels recognizes the TIKTIKPX segment envelope: RGB pixels hold a
// big-endian gzip length followed by compressed MPEG-TS. Ordinary images and
// media remain unchanged. PNG and gzip decoding both have allocation bounds.
func unwrapPixels(body []byte) ([]byte, error) {
	if !bytes.HasPrefix(body, []byte("\x89PNG\r\n\x1a\n")) {
		return body, nil
	}
	cfg, err := png.DecodeConfig(bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("reading PNG segment: %w", err)
	}
	size := uint64(cfg.Width) * uint64(cfg.Height) * 3
	if size > pixelLimit {
		return nil, fmt.Errorf("PNG segment exceeds %d pixel bytes", pixelLimit)
	}
	if size < 12 || body[24] != 8 || (body[25] != 2 && body[25] != 6) {
		return body, nil
	}
	img, err := png.Decode(bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("decoding PNG segment: %w", err)
	}
	rgb := make([]byte, 0, int(size))
	for y := 0; y < cfg.Height; y++ {
		for x := 0; x < cfg.Width; x++ {
			pixel := color.NRGBAModel.Convert(img.At(x, y)).(color.NRGBA)
			rgb = append(rgb, pixel.R, pixel.G, pixel.B)
		}
	}
	if string(rgb[:8]) != "TIKTIKPX" {
		return body, nil
	}
	n := uint64(binary.BigEndian.Uint32(rgb[8:12]))
	if n == 0 || n > uint64(len(rgb)-12) {
		return nil, fmt.Errorf("invalid PNG segment payload length: %d", n)
	}
	reader, err := gzip.NewReader(bytes.NewReader(rgb[12 : 12+n]))
	if err != nil {
		return nil, fmt.Errorf("opening PNG segment payload: %w", err)
	}
	defer reader.Close()
	ts, err := io.ReadAll(io.LimitReader(reader, pixelLimit+1))
	if err != nil {
		return nil, fmt.Errorf("decoding PNG segment payload: %w", err)
	}
	if len(ts) > pixelLimit || len(ts) < 3*188 || len(ts)%188 != 0 {
		return nil, fmt.Errorf("invalid PNG segment MPEG-TS size: %d", len(ts))
	}
	for at := 0; at < len(ts); at += 188 {
		if ts[at] != 0x47 {
			return nil, fmt.Errorf("invalid PNG segment MPEG-TS packet at %d", at)
		}
	}
	return ts, nil
}
