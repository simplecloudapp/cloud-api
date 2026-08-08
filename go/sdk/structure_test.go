package simplecloud

import (
	"bufio"
	"os"
	"path/filepath"
	"testing"
)

const maxHandwrittenFileLines = 120

func TestHandwrittenFilesStayFocused(t *testing.T) {
	files, err := filepath.Glob("*.go")
	if err != nil {
		t.Fatal(err)
	}
	files = append(files, "../simplecloud.go")
	for _, file := range files {
		lineCount, err := countFileLines(file)
		if err != nil {
			t.Errorf("%s: %v", file, err)
			continue
		}
		if lineCount > maxHandwrittenFileLines {
			t.Errorf("%s has %d lines; maximum is %d", file, lineCount, maxHandwrittenFileLines)
		}
	}
}

func countFileLines(path string) (int, error) {
	file, err := os.Open(path)
	if err != nil {
		return 0, err
	}
	defer file.Close()

	lines := 0
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		lines++
	}
	return lines, scanner.Err()
}
