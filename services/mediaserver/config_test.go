package mediaserver

import (
	"context"
	"testing"

	"github.com/urfave/cli/v3"

	"github.com/stupside/castor/internal/settings"
)

func TestDesktopToolPathsStillUseTheInstalledNames(t *testing.T) {
	want := FFmpegConfig{Path: "ffmpeg", ProbePath: "ffprobe"}
	if got := defaults().FFmpeg; got != want {
		t.Fatalf("default tool paths = %#v, want %#v", got, want)
	}
}

func TestNativePackageToolPathsReachTheConfiguration(t *testing.T) {
	want := FFmpegConfig{Path: "/data/app/castor/lib/arm64/libffmpeg.so", ProbePath: "/data/app/castor/lib/arm64/libffprobe.so"}
	t.Setenv("CASTOR_FFMPEG__PATH", want.Path)
	t.Setenv("CASTOR_FFMPEG__PROBE_PATH", want.ProbePath)
	cmd := &cli.Command{Flags: []cli.Flag{settings.Flag}}
	cmd.Action = func(_ context.Context, cmd *cli.Command) error {
		got, err := settings.Load(cmd, defaults())
		if err != nil {
			t.Fatal(err)
		}
		if got.FFmpeg != want {
			t.Fatalf("tool paths = %#v, want %#v", got.FFmpeg, want)
		}
		return nil
	}
	if err := cmd.Run(t.Context(), []string{"media"}); err != nil {
		t.Fatal(err)
	}
}
