# Debloat method

How the 47-package profile in [`../profiles/`](../profiles/) was produced —
the same loop works on most Android TV devices:

1. **Back up first.** `pm list packages`, `pm list packages -d`,
   `getprop`, and the global/secure/system settings tables, saved off-device
   before touching anything. Write the restore path *before* the debloat:
   every disabled package must be one `pm enable` away from coming back.
2. **Sort the full package list** (~90 packages on this stick) into:
   keep (things the household actually uses), disable (telemetry, content
   rows, preloads, plumbing with no purpose on this device), and
   don't-touch (input, display, network, update-adjacent system core).
3. **Disable in small groups**, rebooting between groups and living with
   each state. Anything that misbehaves gets re-enabled and noted; the
   profile only lists packages that stayed disabled in daily use.
4. **Replace the launcher last**, once the stock one is no longer needed —
   and keep the stock launcher *disabled, not uninstalled*, so recovery is
   trivial.
5. **Kill the background noise too**: animations off, Wi-Fi/Bluetooth
   background scanning off, screensaver off, location off — on a 1 GB stick
   these matter as much as the package list.
6. **Keep it off the update treadmill deliberately**: the firmware update
   service sits in the profile's optional group. The reference unit stays
   on the firmware the profile was verified against (PI.2055).

What the profile deliberately does **not** do: uninstall anything for the
system user (irreversible without a factory image), touch the keyboard,
network, HDMI or input stack, or disable the Play Store — the stick still
has to be a normal, maintainable Android TV afterwards.
