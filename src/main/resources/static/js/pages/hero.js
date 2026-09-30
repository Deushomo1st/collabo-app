// The landing hero: the page unveils, the title's letters rise out of their lines, the rest follows in a chain, the marquees rise
// from below the fold, and the closing title is scrubbed in by scroll. Smooth scroll underneath.
// Needs gsap, ScrollTrigger, SplitText and Lenis (loaded by the page); without them, or with reduced motion, the page just shows.
const { gsap, ScrollTrigger, SplitText } = window;
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
const $ = (id) => document.getElementById(id);

// The marquee chips: the kinds of people spaces look for.
const ROLES = ['Designer', 'Backend dev', 'Illustrator', 'Founder', 'Video editor', 'Game dev', 'Writer', 'Data nerd', 'Musician', 'Marketer', 'Mobile dev', 'Researcher'];
function fillTrack(track, offset) {
    const chips = ROLES.slice(offset).concat(ROLES.slice(0, offset)).map((r) => `<span class="hr-chip"><b>+</b> ${r}</span>`).join('');
    track.innerHTML = chips + chips;   // twice, so the loop has no seam
}

// Buttons: the label and its hover text (data-hover, else the same words) sit in one cell. On hover the label's characters
// roll up and out while the hover text's roll in from below, letter by letter.
function rollButtons() {
    const line = (text, cls) => {
        const row = document.createElement('span');
        row.className = 'ln ' + cls; row.setAttribute('aria-hidden', 'true');
        [...text].forEach((c, i) => {
            const s = document.createElement('span');
            s.className = 'ch'; s.textContent = c; s.style.transitionDelay = i * 0.02 + 's';
            row.appendChild(s);
        });
        return row;
    };
    document.querySelectorAll('[data-chars]').forEach((btn) => {
        const label = btn.querySelector('span');
        const text = label.textContent;
        btn.setAttribute('aria-label', text);   // screen readers get the label once, not letter by letter
        label.textContent = '';
        label.append(line(text, 'ln--out'), line(btn.dataset.hover || text, 'ln--in'));
    });
}

function marquees() {
    const loops = [...document.querySelectorAll('.hr-marquee')].map((m, i) => {
        const track = m.firstElementChild;
        fillTrack(track, i * 5);
        const left = Number(m.dataset.dir) < 0;
        return gsap.fromTo(track, { xPercent: left ? 0 : -50 }, { xPercent: left ? -50 : 0, duration: 38, ease: 'none', repeat: -1 });
    });
    // scrolling down runs them one way, scrolling back up runs them the other
    let last = 0;
    ScrollTrigger.create({ start: 0, end: 'max', onUpdate: (s) => {
        if (s.direction === last) return;
        last = s.direction;
        loops.forEach((t) => gsap.to(t, { timeScale: s.direction, duration: .4 }));
    } });
}

function intro() {
    document.body.classList.remove('is-loading');
    const title = SplitText.create('#hr-title', { type: 'lines,words,chars', mask: 'lines' });
    const sub = SplitText.create('#hr-sub', { type: 'lines', mask: 'lines' });
    gsap.timeline({ defaults: { ease: 'expo.out' } })
        .to('#veil', { opacity: 0, filter: 'blur(10px)', duration: .9, ease: 'power2.out', onComplete: () => $('veil').remove() })
        .from(title.chars, { yPercent: 200, duration: 1.6, stagger: .04 }, .15)
        .from(sub.lines, { yPercent: 100, opacity: 0, duration: .8, stagger: .08 }, '<50%')
        .from('#hr-btn', { yPercent: 40, opacity: 0, duration: .8 }, '<44%')
        .from('#hr-live', { y: 12, opacity: 0, filter: 'blur(10px)', duration: .8 }, '<12%')
        .from('#hr-marquees', { y: '40vh', duration: 1.5, ease: 'power2.out' }, '<40%');

    // the closing title: its letters are scrubbed in by the scroll itself
    const big = SplitText.create('#hr-big', { type: 'lines,words,chars', mask: 'lines' });
    gsap.from(big.chars, { yPercent: 200, ease: 'expo.out', stagger: .04, scrollTrigger: { trigger: '#trigger', start: 'top 60%', end: 'center 40%', scrub: true } });
}

function smoothScroll() {
    if (!window.Lenis) return;
    const lenis = new window.Lenis({ lerp: .1 });
    lenis.on('scroll', ScrollTrigger.update);
    gsap.ticker.add((t) => lenis.raf(t * 1000));
    gsap.ticker.lagSmoothing(0);
}

function boot() {
    rollButtons();
    if (!gsap || !ScrollTrigger || !SplitText || reduced) {   // no motion: just the page
        $('veil')?.remove();
        document.querySelectorAll('.hr-track').forEach((t, i) => fillTrack(t, i * 5));
        return;
    }
    gsap.registerPlugin(ScrollTrigger, SplitText);
    document.body.classList.add('is-loading');
    smoothScroll();
    marquees();
    // wait for the font so the letters split on the real glyph widths
    (document.fonts?.ready || Promise.resolve()).then(intro);
}
boot();
