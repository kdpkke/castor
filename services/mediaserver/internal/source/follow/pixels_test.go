package follow

import (
	"bytes"
	"compress/gzip"
	"encoding/binary"
	"hash/crc32"
	"image"
	"image/color"
	"image/png"
	"testing"
)

func pixelEnvelope(t *testing.T, payload []byte, declared int) []byte {
	t.Helper()
	header := append([]byte("TIKTIKPX"), binary.BigEndian.AppendUint32(nil, uint32(declared))...)
	rgb := append(header, payload...)
	const width = 64
	height := (len(rgb) + width*3 - 1) / (width * 3)
	img := image.NewNRGBA(image.Rect(0, 0, width, height))
	for at := 0; at < width*height; at++ {
		var c [3]byte
		for k := range c {
			if index := 3*at + k; index < len(rgb) {
				c[k] = rgb[index]
			}
		}
		img.SetNRGBA(at%width, at/width, color.NRGBA{R: c[0], G: c[1], B: c[2], A: 255})
	}
	var out bytes.Buffer
	if err := png.Encode(&out, img); err != nil {
		t.Fatal(err)
	}
	return out.Bytes()
}

func gzipped(t *testing.T, body []byte) []byte {
	t.Helper()
	var out bytes.Buffer
	w := gzip.NewWriter(&out)
	if _, err := w.Write(body); err != nil {
		t.Fatal(err)
	}
	if err := w.Close(); err != nil {
		t.Fatal(err)
	}
	return out.Bytes()
}

func TestPixelSegmentYieldsTheExactTransportStream(t *testing.T) {
	ts := bytes.Repeat([]byte{0x55}, 188*9)
	for at := 0; at < len(ts); at += 188 {
		ts[at] = 0x47
	}
	compressed := gzipped(t, ts)
	got, err := unwrapPixels(pixelEnvelope(t, compressed, len(compressed)))
	if err != nil || !bytes.Equal(got, ts) {
		t.Fatalf("decoded segment differs: %d bytes, %v", len(got), err)
	}
}

func TestOrdinaryMediaAndImagesStayUnchanged(t *testing.T) {
	var picture bytes.Buffer
	if err := png.Encode(&picture, image.NewRGBA(image.Rect(0, 0, 4, 4))); err != nil {
		t.Fatal(err)
	}
	for _, body := range [][]byte{[]byte("ordinary transport stream"), picture.Bytes()} {
		got, err := unwrapPixels(body)
		if err != nil || !bytes.Equal(got, body) {
			t.Fatalf("ordinary media changed: %v", err)
		}
	}
}

func TestPixelSegmentRejectsInvalidPayloadsAndAllocationBombs(t *testing.T) {
	goodGzip := gzipped(t, bytes.Repeat([]byte{0x47}, 3*188))
	badCRC := bytes.Clone(goodGzip)
	badCRC[len(badCRC)-8] ^= 1
	bomb := pixelEnvelope(t, goodGzip, len(goodGzip))
	binary.BigEndian.PutUint32(bomb[16:20], 1<<20)
	binary.BigEndian.PutUint32(bomb[20:24], 1<<20)
	binary.BigEndian.PutUint32(bomb[29:33], crc32.ChecksumIEEE(bomb[12:29]))
	for name, body := range map[string][]byte{
		"pixel allocation": bomb,
		"length":           pixelEnvelope(t, goodGzip, 1<<20),
		"gzip checksum":    pixelEnvelope(t, badCRC, len(badCRC)),
		"not MPEG-TS":      pixelEnvelope(t, gzipped(t, []byte("not a transport stream")), len(gzipped(t, []byte("not a transport stream")))),
	} {
		t.Run(name, func(t *testing.T) {
			if _, err := unwrapPixels(body); err == nil {
				t.Fatal("invalid segment accepted")
			}
		})
	}
	compressed := gzipped(t, bytes.Repeat([]byte{0x47}, pixelLimit+1))
	if _, err := unwrapPixels(pixelEnvelope(t, compressed, len(compressed))); err == nil {
		t.Fatal("unbounded gzip output accepted")
	}
}
