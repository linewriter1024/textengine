package textengine

import "strconv"

type Time int64

func (game *Game) AddTimeSpan(timespan Time) {
	game.time += timespan
}

func (time Time) String() string {
	return strconv.FormatInt(int64(time), 10)
}

func NewTime(s string) (Time, error) {
	timeint, err := strconv.ParseInt(s, 10, 64)
	return Time(timeint), err
}
