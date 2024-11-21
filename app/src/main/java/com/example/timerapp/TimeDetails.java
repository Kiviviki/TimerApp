package com.example.timerapp;

import java.util.Date;

public class TimeDetails {
    private Date startTime;
    private Date endTime;

    // Constructor with no start time initially
    public TimeDetails() {
        this.startTime = null;  // Set start time as null initially
        this.endTime = null;
    }

    // Constructor when start time is passed
    public TimeDetails(Date startTime) {
        this.startTime = startTime;
        this.endTime = null;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }
}
