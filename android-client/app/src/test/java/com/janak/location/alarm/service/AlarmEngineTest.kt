package com.janak.location.alarm.service

import com.janak.location.alarm.alarm.AlarmEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEngineTest {

    @Test
    fun testDistanceAlarmTriggered() {
        val triggered = AlarmEngine.shouldTriggerAlarm(
            distanceMeters = 450.0,
            distanceThresholdMeters = 500.0,
            isDistanceAlarmEnabled = true,
            etaMinutes = 15.0,
            predictiveMinutesThreshold = 10,
            isPredictiveAlarmEnabled = false,
            isRailway = false
        )
        assertTrue(triggered)
    }

    @Test
    fun testDistanceAlarmNotTriggeredWhenDisabled() {
        val triggered = AlarmEngine.shouldTriggerAlarm(
            distanceMeters = 450.0,
            distanceThresholdMeters = 500.0,
            isDistanceAlarmEnabled = false,
            etaMinutes = 15.0,
            predictiveMinutesThreshold = 10,
            isPredictiveAlarmEnabled = false,
            isRailway = false
        )
        assertFalse(triggered)
    }

    @Test
    fun testPredictiveTimeAlarmTriggered() {
        val triggered = AlarmEngine.shouldTriggerAlarm(
            distanceMeters = 5000.0,
            distanceThresholdMeters = 500.0,
            isDistanceAlarmEnabled = false,
            etaMinutes = 8.0,
            predictiveMinutesThreshold = 10,
            isPredictiveAlarmEnabled = true,
            isRailway = false
        )
        assertTrue(triggered)
    }

    @Test
    fun testRailwayBulletproof2kmTriggerWhenAllAlarmsDisabled() {
        // Even if the commuter turned off both distance and time alarms,
        // entering within 2000m on a train MUST fire the bulletproof alarm!
        val triggered = AlarmEngine.shouldTriggerAlarm(
            distanceMeters = 1800.0,
            distanceThresholdMeters = 500.0,
            isDistanceAlarmEnabled = false,
            etaMinutes = 25.0,
            predictiveMinutesThreshold = 10,
            isPredictiveAlarmEnabled = false,
            isRailway = true
        )
        assertTrue(triggered)
    }

    @Test
    fun testRailwayOutsideBulletproofRangeDoesNotTriggerIfAlarmsDisabled() {
        val triggered = AlarmEngine.shouldTriggerAlarm(
            distanceMeters = 3500.0,
            distanceThresholdMeters = 500.0,
            isDistanceAlarmEnabled = false,
            etaMinutes = 25.0,
            predictiveMinutesThreshold = 10,
            isPredictiveAlarmEnabled = false,
            isRailway = true
        )
        assertFalse(triggered)
    }
}
