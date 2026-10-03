// A chat bubble that speaks from a puppet: your own picture on a string drops from the top right corner like a jack-in-the-box, the bubbles come out under it,
// and when the last one has faded (or one is tapped) it springs back up. Bubbles line up, so two never talk over each other.
// `pic` (a username) puts that person's profile picture in front of the words; `other` puts a second one after them ("a removed b"); `href` is where a tap on the bubble goes.
import { h } from '/js/services/dom.js';
import { face } from '/js/services/face.js';
import { currentUser } from '/js/services/api.js';

const SHOW = 3000;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let line = Promise.resolve(), waiting = 0, puppet = null, me;

async function lower() {
    if (puppet) return;
    me ??= (await currentUser().catch(() => null))?.username;
    puppet = h('div', { class: 'dangle', 'aria-hidden': 'true' }, h('div', { class: 'dangle__swing' }, h('span', { class: 'dangle__string' }), me ? face(me, 'dangle__face') : h('span', { class: 'sp-avatar dangle__face' })));
    document.body.append(puppet);
    await sleep(1000);   // the drop and the first spring
}

async function raise() {
    const p = puppet; if (!p) return;
    puppet = null; p.classList.add('is-up');
    await sleep(600); p.remove();
}

async function say(text, pic, other, href) {
    await lower();
    const el = h('div', { class: `speech is-right${href ? ' is-link' : ''}`, role: 'status' }, pic && face(pic, 'sp-avatar--sm'), h('span', { text }), other && face(other, 'sp-avatar--sm'));
    document.body.append(el);
    const tapped = new Promise((r) => el.addEventListener('click', () => r(true), { once: true }));
    await sleep(30); el.classList.add('is-on');
    const tap = await Promise.race([sleep(SHOW).then(() => false), tapped]);
    el.classList.remove('is-on');
    if (tap && href) { raise(); await sleep(350); location.href = href; return; }   // up it goes, then the bubble's address opens
    await sleep(700); el.remove();
}

export const speak = (text, pic, other, href) => {
    waiting++;
    return (line = line.then(() => say(text, pic, other, href)).catch(() => {}).then(() => { if (!--waiting) return raise(); }));
};
