package io.pleo.antaeus.schedule.service

import org.quartz.SchedulerException

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

interface JobSchedule {

    fun nextDate(): ZonedDateTime {
        return LocalDate.now()
                .with(TemporalAdjusters.firstDayOfNextMonth())
                .atStartOfDay(ZoneId.systemDefault())
    }

    /**
     * Tell quartz to schedule the job using our `[JobSchedulingService.trigger]`
     * @param zonedDateTime
     * @throws SchedulerException
     */
    @Throws(SchedulerException::class)
    fun schedule(zonedDateTime: ZonedDateTime)
}
