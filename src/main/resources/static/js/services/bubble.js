// A chat bubble that speaks from the menu button, tail pointing up at it: it comes out, stays 3 s and goes back in.
// Bubbles line up, so two never talk over each other. `pic` (a username) puts that person's profile picture in front of the words; `other` puts a second one after them ("a removed b").
import { h } from '/js/services/dom.js';
import { face } from '/js/services/face.js';

const SHOW = 3000;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let line = Promise.resolve();

async function say(text, pic, other) {
    const el = h('div', { class: 'speech', role: 'status' }, pic && face(pic, 'sp-avatar--sm'), h('span', { text }), other && face(other, 'sp-avatar--sm'));
    const b = document.querySelector('.nav-menu')?.getBoundingClientRect();
    const right = !b || b.left + b.width / 2 > innerWidth / 2;
    el.classList.add(right ? 'is-right' : 'is-left');
    el.style.top = `${(b ? b.bottom : 56) + 12}px`;
    el.style[right ? 'right' : 'left'] = `${b ? Math.max(8, right ? innerWidth - b.right : b.left) : 16}px`;
    document.body.append(el);
    await sleep(30); el.classList.add('is-on');
    await sleep(SHOW); el.classList.remove('is-on');
    await sleep(700); el.remove();
}

export const speak = (text, pic, other) => (line = line.then(() => say(text, pic, other)).catch(() => {}));
