# Silly ideas

Easter eggs and fortune-cookie messages. Everything here should be rare, quick to skip, and never in
the way of a move. Many of the jokes play on what's unusual about this app: every deal is proven
winnable, a solver has already won your deal, and there's no internet at all.

## Win messages (fortune cookie style)

One short line under "You won", picked at random or based on how the game went.

- **Classic fortunes**: "You will soon find a red seven where you need one." "A face-down card is just a
  friend you haven't met." "Patience is a virtue. You have demonstrated it. Barely."
- **Based on your stats**: fastest win → "Did you even look at the cards?"; 400 moves → "The scenic
  route. Respect."; 0 undos → "No take-backs. Bold."; 80 undos → "Time is a flat circle and you went
  round it 80 times."
- **Jokes about the proof**: "This deal was proven winnable. You have now proven it twice." Or, if you
  won in fewer moves than the stored solution: "You beat the solver by 14 moves. It's sulking."
- **Jokes about the solver**: "The solver needed 0.003 seconds. You needed 11 minutes. Both valid."
- **Difficulty jokes**: Expert win → "The test bot lost this one 30 times out of 30. You didn't."
- **Fake lottery numbers** made from the deal number: "Your lucky numbers: 4, 17, 23. Deal #1234 thanks
  you."
- **Fortunes that are tips in disguise**: "Wise one empties a column before they need it."
- **A fortune that "continues" next win**: a few fortunes run as a slow serial story, one line per win.
- **Absurd "... in bed" style lines**, kept family-friendly: "...on a Tuesday."

## Win animations

- **The classic Windows bouncing-cards cascade**, as a rare (1 in 50) win animation, or always on when a
  setting is on. Cards are drawn in code already, so this is mostly physics.
- **Cards fly off and spell your win count**, or "GG".
- **The foundation Kings take a little bow.**
- **Confetti made of tiny pips** (♠♥♦♣).
- **On a very fast win, the cards briefly hold up scorecards**: "9.5, 9.7, 10".

## Hidden in the cards

The card faces are drawn in code, so anything can go on them.

- **A rare card face**: about 1 in 5,000 Jacks winks, or a King wears sunglasses. Purely visual.
- **The Queen of Hearts looks a bit annoyed** when a King of the wrong colour is dragged over her.
- **The Joker that doesn't exist**: at a tiny chance, a card back shows a Joker peeking out for a moment.
- **Card backs that change on holidays**: pumpkins on Halloween, snowflakes in December, hearts on
  14 February, a cake on the app's "birthday" (first commit date).
- **Ace of Spades with an ornate flourish**, as on real decks, with a tiny "made in Kotlin" in its corner.
- **Your initials on a card**, shown when you win deal #1 (or a deal number equal to your birthday).

## Easter eggs you trigger yourself

- **Tap the title or the deal number 7 times** for a joke "developer mode" toast: "You are now a
  developer. Please fix the bugs."
- **Special deal codes** in the New game box:
  - `K1-42` gives the deal and a message: "The answer, and also a deal."
  - `XX-404`: "Deal not found. Unlike your keys, which are in your other coat."
  - `UP-UP-DN-DN`, the Konami code, unlocks a silly card back.
  - `SC-0` or `K1-0`: "Deal zero is a sorted deck. Don't get excited." It could even play a trivially
    easy sorted game (as a joke, outside the stats, still proven by `ProofVerifier`).
- **Shake the phone** to wiggle the stock (animation only, nothing changes in the game).
- **Long-press the stock when it's empty**: "There is nothing here. There has never been anything here."
- **Try to put a card on itself**: on the 10th try in a game, "That's not how cards work."
- **Undo 100 times in one game**: a little toast: "Time traveller."
- **Tap a foundation Ace 5 times**: it flips over and shows the card back is also a card back. Very deep.

## Stuck and loss moments (gentle)

These must never feel like nagging, and must never show in the middle of a move.

- **When you've been stuck a while**, one optional line in the menu: "Psst. It's winnable. That's not a
  threat."
- **On Restart**: "Again! Same cards, new you."
- **When you're stuck on an Expert deal**, the solver says it's "rooting for you".

## Stats and achievements (silly ones)

Kept on the phone, never pushed at you; only visible in the stats screen.

- **Silly titles** from your stats: "Undo Enthusiast", "Draw-3 Masochist", "Spider Whisperer", "Kinged"
  (filled an empty column with a King 500 times).
- **Absurd totals**: "Cards moved: 48,211. That's about 3.4 km of cards, laid end to end."
- **Time played in silly units**: "That's 14 episodes of a long TV show" or "3 transatlantic flights".
- **A "Most played deal" stat** with a sarcastic note if you restarted one deal 12 times.

## The no-internet joke

- **A fake "Online leaderboard"** item in settings that only says: "You are #1. You are the only player.
  There is no internet here."
- **"Cloud sync"** that shows a picture of a little cloud and "it's just a drawing."
- **About screen**: "0 ads, 0 trackers, 0 servers, 1 solver who believes in you."

## Calendar and time

- **Playing at 3 a.m.**: "Shouldn't you be asleep? (Won anyway.)"
- **Friday the 13th**: deal number 13 by default, black cat card back.
- **Pi day (14 March)**: deal codes ending in 314 give a message.
- **New Year's**: first win of the year gets fireworks.
- **The app's birthday**: "Happy birthday to me. Thanks for playing."

## Developer jokes (also safe in release)

- **Hidden credits** listing the solver as a co-author: "Solver: did all the hard work. Took no credit."
- **Commit-message style fortunes**: "fix: you, mostly".
- **A solver stat after a win**: "Solver nodes searched for this deal: 18,442. You checked about 30."

## How to keep it from getting in the way

- Win messages: one line, small text, under the buttons, never a popup.
- Rare events (bouncing cards, odd card faces): low odds, and one on/off setting, "Silliness", on by
  default.
- Nothing slows a move, blocks a tap or changes the game. Visual effects only.
- Make it deterministic per deal (seed the joke from the deal number), so friends who share a deal code
  get the same fortune.

## Picks to start with

1. **Fortune line on the win screen**, mixing random fortunes with lines based on the game (undos, time,
   moves compared with the solver). Cheap, and the most fun per line of code.
2. **The bouncing-cards win animation** as a rare surprise.
3. **Special deal codes** (`K1-0`, `404`, `42`), since the code box already exists.
4. **Holiday card backs**, starting with Halloween (31 October).
