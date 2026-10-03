// Profile reminders: on your own profile, what is still missing comes out as a chat bubble; the next one follows 30 s later.
import { speak } from '/js/services/bubble.js';

const GAP = 30000;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** steps: [[missing(), text], …] in the order they should come out; a step is skipped once it is no longer missing. */
export async function profileBubbles(steps) {
    let first = true;
    for (const [missing, text] of steps) {
        if (!(await missing())) continue;
        if (!first) { await sleep(GAP); if (!(await missing())) continue; }   // it may have been done while waiting
        first = false;
        await speak(text);
    }
}
