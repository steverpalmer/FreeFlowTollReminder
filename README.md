# FreeFlowTollReminder

Copyright 2019 Steve Palmer

Android App to create a reminder to pay toll fees after using a free
flow toll road.

The UK has introduced a [free flow toll
road](https://en.wikipedia.org/wiki/Open_road_tolling) with the
[Mersey Gateway Bridge](http://www.merseygateway.co.uk/).  The bridge
itself has no toll booths or anything to stop the free flow of
traffic, but you need to remember to pay the tolls at the [Merseyflow
website](https://www.merseyflow.co.uk/).  Unfortunately, after driving
on for another hour or so, it is very easy to forget and so incur a
penalty of £20.  I've designed this android app for my mobile that
detects when I've crossed the toll road and adds an event with
reminders to a calendar.

## Status

The app is still fairly basic, but it has worked consistently on both
real and test toll roads.  I'm in the process of trying to release it
on Google Play Store.

As far as I am aware, the only real free flow toll roads in the UK are
the Mersey Gateway Bridge and the Silver Jubilee Bridge.  The app is
configured to detect usage of both of these toll roads.

## Operation

[It is illegal to hold a phone or sat nav while driving or riding a
motorcycle in the
UK.](https://www.gov.uk/using-mobile-phones-when-driving-the-law)
Therefore, I've designed the app to be *non-interactive* as far as
possible.

When run, the app may ask you for certain privileges, specifically:
 * access the device's location - need to detect toll road usage;
 * access your calendar - to generate the reminders.
Without both these privileges, the app cannot function.  Otherwise,
the main screen allows you to select the calendar to which it will add
reminder events.  This is the only interaction available in the app;
everything else is entirely automatic, and the app can be closed.

The app starts a service in the background which detects the toll road
usage and adding calendar reminder.  This service should continue
running until the phone is rebooted.  You also have the option of
clicking "Finish" from the app to stop the service and close the app.

The reminder event is set to "Pay [toll road name] Toll" for tomorrow
at noon with the default reminders.  The MerseyFlow rules require
payment "by 11.59pm the day after your crossing".  However, if you are
crossing the bridge at midnight, exactly which "day" did you cross?
In such situations, the app takes a conservative approach and errs on
the side of an early reminder rather than too late.

