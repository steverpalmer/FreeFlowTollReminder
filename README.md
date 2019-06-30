# FreeFlowTollReminder

Copyright 2019 Steve Palmer

Android App to create a reminder to pay toll fees after using a free
flow toll road.

The UK has introduced a "free flow toll road" with the [Mersey Gateway
Bridge](http://www.merseygateway.co.uk/).  The bridge itself has no
toll boths or anything to stop the free flow of traffic, but you need
to remember to pay the tolls at the [Merseyflow
website](https://www.merseyflow.co.uk/).  Unfortunately, after driving
on for another hour or so, it is very easy to forget and so incurr a
penalty of £20.  I've designed this android app for my mobile that
detects when I've crossed the toll road and adds an event with
reminders to a calendar.

## Status

This is still very much *Work-In-Progress* and I provide *no
guarantees*.  However, it has worked correctly for me on a "Test Toll
Road"; a local stretch of road that I've defined as a toll road for
test purposes.  Unfortunately, to get it to work I need to display
Google Maps on the phone!

As far as I am aware, the only real free flow toll roads in the UK are
the Mersey Gateway Brdige and the Silver Jubilee Bridge.  The app is
configured to detect usage of both of these toll roads.

## Operation

[It is illegal to hold a phone or sat nav while driving or riding a
motorcycle in the
UK.](https://www.gov.uk/using-mobile-phones-when-driving-the-law)
Therefore, I've designed the app to be non-interactive as far as
possible.

When run, the App screen allows you to select the calendar to which it
will add the reminder events.  This is the only interaction available
in the app - everything else is entirely automatic, and the app can be
closed.

The app starts a service in the background which does the "heavy
lifting" of detecting the toll road usage and adding calendar
reminder.  This service should continue running until the phone is
rebooted.  You also have the option of clicking "Finish" from the app
to stop the service and close the app.

The reminder event is set to "Pay Toll" for tommorrow at noon with the
default reminders.  There might be some querks it you pass over the
bridge around midnight - I haven't got to worrying about this yet.
