package com.benleskey.textengine.plugins.games.highfantasy;

import com.benleskey.textengine.model.DTime;

/**
 * GameCalendar converts DTime (milliseconds since epoch) to/from human-readable
 * calendar format.
 * 
 * Calendar system:
 * - 12 months (January-December) of 30 days each = 360 days per year
 * - 24-hour days
 * - Epoch (time 0) = 00:00:00 January 1, Year 100
 * 
 * This is a high fantasy game-specific calendar implementation.
 */
public class GameCalendar {
	private static final int EPOCH_YEAR = 100;
	private static final int DAYS_PER_MONTH = 30;
	private static final int MONTHS_PER_YEAR = 12;
	private static final int DAYS_PER_YEAR = DAYS_PER_MONTH * MONTHS_PER_YEAR; // 360
	private static final long MILLISECONDS_PER_SECOND = 1000;
	private static final long SECONDS_PER_MINUTE = 60;
	private static final long MINUTES_PER_HOUR = 60;
	private static final long HOURS_PER_DAY = 24;
	private static final long MILLISECONDS_PER_DAY = MILLISECONDS_PER_SECOND * SECONDS_PER_MINUTE * MINUTES_PER_HOUR
			* HOURS_PER_DAY;

	private static final String[] MONTH_NAMES = {
			"January", "February", "March", "April", "May", "June",
			"July", "August", "September", "October", "November", "December"
	};

	/**
	 * Convert DTime to calendar components.
	 */
	public static CalendarDate fromDTime(DTime time) {
		long totalMilliseconds = time.toMilliseconds();

		// Calculate total days since epoch
		long totalDays = totalMilliseconds / MILLISECONDS_PER_DAY;
		long remainingMilliseconds = totalMilliseconds % MILLISECONDS_PER_DAY;

		// Calculate year, month, day
		int year = EPOCH_YEAR + (int) (totalDays / DAYS_PER_YEAR);
		long dayOfYear = totalDays % DAYS_PER_YEAR;
		int month = (int) (dayOfYear / DAYS_PER_MONTH); // 0-11
		int day = (int) (dayOfYear % DAYS_PER_MONTH) + 1; // 1-30

		// Calculate time of day
		long totalSeconds = remainingMilliseconds / MILLISECONDS_PER_SECOND;
		int hour = (int) (totalSeconds / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR));
		int minute = (int) ((totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR);
		int second = (int) (totalSeconds % SECONDS_PER_MINUTE);
		int millisecond = (int) (remainingMilliseconds % MILLISECONDS_PER_SECOND);

		return new CalendarDate(year, month, day, hour, minute, second, millisecond);
	}

	/**
	 * Convert calendar components to DTime.
	 */
	public static DTime toDTime(int year, int month, int day, int hour, int minute, int second, int millisecond) {
		// Calculate days since epoch
		long yearsSinceEpoch = year - EPOCH_YEAR;
		long totalDays = yearsSinceEpoch * DAYS_PER_YEAR + month * DAYS_PER_MONTH + (day - 1);

		// Calculate time within day
		long totalSeconds = hour * MINUTES_PER_HOUR * SECONDS_PER_MINUTE
				+ minute * SECONDS_PER_MINUTE
				+ second;

		long totalMilliseconds = totalDays * MILLISECONDS_PER_DAY
				+ totalSeconds * MILLISECONDS_PER_SECOND
				+ millisecond;

		return DTime.fromMilliseconds(totalMilliseconds);
	}

	/**
	 * Format time as HH:MM:SS
	 */
	public static String formatTime(DTime time) {
		CalendarDate date = fromDTime(time);
		return String.format("%02d:%02d:%02d", date.hour(), date.minute(), date.second());
	}

	/**
	 * Format date as "Month Day, Year"
	 */
	public static String formatDate(DTime time) {
		CalendarDate date = fromDTime(time);
		return String.format("%s %d, %d", MONTH_NAMES[date.month()], date.day(), date.year());
	}

	/**
	 * Format full date and time as "HH:MM:SS, Month Day, Year"
	 */
	public static String formatFull(DTime time) {
		CalendarDate date = fromDTime(time);
		return String.format("%02d:%02d:%02d, %s %d, %d",
				date.hour(), date.minute(), date.second(),
				MONTH_NAMES[date.month()], date.day(), date.year());
	}

	/**
	 * Get month name by index (0-11).
	 */
	public static String getMonthName(int monthIndex) {
		return MONTH_NAMES[monthIndex];
	}

	/**
	 * Calendar date record.
	 * 
	 * @param year        Year (100+)
	 * @param month       Month (0-11, 0=January)
	 * @param day         Day of month (1-30)
	 * @param hour        Hour (0-23)
	 * @param minute      Minute (0-59)
	 * @param second      Second (0-59)
	 * @param millisecond Millisecond (0-999)
	 */
	public record CalendarDate(
			int year,
			int month,
			int day,
			int hour,
			int minute,
			int second,
			int millisecond) {
	}
}
