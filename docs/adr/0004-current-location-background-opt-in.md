# Background location is an opt-in, not a requirement

A widget that follows the Current location only stays accurate while the user travels if the app
can read the location in the background (`ACCESS_BACKGROUND_LOCATION`, "Allow all the time").
That permission is optional. It is offered with an explanation where it matters (widget setup and
settings), and never during onboarding. Without it, the Current location updates whenever the app
is opened or a widget is tapped, and background Refreshes use the last known position. We chose
this to keep onboarding light and to avoid a permission that worries users and reviewers.
