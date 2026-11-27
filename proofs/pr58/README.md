# #58 premium, debug build on the emulator (API 36), 27/11 ~01 h 45

Debug build with `qa_premium=fresh|off` and `qa_plans=sample`: the plans and prices on the paywall are
**debug samples** showing the layout, not Play's. Play's real plans need a Play-distributed build on a
phone with a Google account (not seen yet).

1-2: onboarding (object, then spaces). 3: paywall after picking "Hallway", light and dark.
4: free user on the door check — the door verdict is free; car boot, hallway, lift and the AR tape show a lock.
5: tapping "Hallway" opens the paywall with that headline. No crash in `logcat -b crash`.
