// The team as cards (role ribbon, picture, permission dots); open one for its permissions. Same look as the Workspace prototype.
// membersSection(space, { members, me, refresh }) -> element. Anyone on the team may open a card; only who manages the team may change it.
// Text goes in through textContent only.
import { openGlassBlurDialog, glassBlurConfirm } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { createSwitch } from '/js/components/switch/switch.js';
import { openFlag } from '/js/components/space/removals.js';
import { spaceMemberUpdate, spaceMemberRemove } from '/js/services/api.js';
import { face } from '/js/services/face.js';
import { h, toast, profileHref } from '/js/services/dom.js';

export const PERMS = [   // same order as SpacePermission on the server
    ['LOG_MILESTONES', 'Log and fulfil milestones', false],
    ['LOG_PAYMENTS', 'Log payment records', false],
    ['ACCEPT_MEMBERS', 'Accept and remove members', true],
    ['EDIT_SETTINGS', 'Edit space settings', true],
    ['POST_IN_ROOM', 'Pin in the room', true],
    ['MANAGE_RECRUITMENT', 'Manage re-recruitment posts', true],
];

const kind = (m) => (m.owner ? 'Owner' : 'Member');

export function membersSection(space, { members, me, refresh }) {
    return h('section', {},
        h('h2', { class: 'sp-h2', text: `Members · ${members.length}` }),
        h('p', { class: 'sp-sub', text: 'Titles show above each face. Open a member to see their permissions.' }),
        h('div', { class: 'sp-grid' }, ...members.map((m) =>
            h('button', { class: 'sp-card sp-glass', type: 'button', 'aria-label': `Open ${m.person.username}`, onclick: () => openTablet(space, m, { me, refresh }) },
                h('span', { class: 'sp-role', text: m.title || kind(m) }),
                face(m.person.username, 'sp-avatar--lg'),
                h('h3', { text: m.person.username }),
                h('small', { text: m.person.preferredTitle ? `${kind(m)} · ${m.person.preferredTitle}` : kind(m) }),
                h('span', { class: 'sp-perm-dots', title: `${m.owner ? PERMS.length : m.permissions.length} of ${PERMS.length} permissions` },
                    ...PERMS.map(([k]) => h('i', { class: m.owner || m.permissions.includes(k) ? 'on' : '' })))))));
}

async function openTablet(space, m, { me, refresh }) {
    const { panel, close } = await openGlassBlurDialog({ size: 'md', label: m.person.username, html: '<div class="sp-tablet"></div>' });
    const tablet = panel.querySelector('.sp-tablet');
    const mine = m.person.username === me.username;
    const may = space.canManage && !m.owner;        // the owner's permissions are fixed
    let perms = [...m.permissions], title = m.title;

    // One call saves the whole set; a refusal puts the switch back.
    const save = async (changes, confirm = false) => {
        const next = await spaceMemberUpdate(space.id, m.person.username, { title, permissions: perms, confirm, ...changes });
        perms = next?.permissions ?? changes.permissions ?? perms;
        refresh();
    };

    const rows = PERMS.map(([key, label, weighty]) => {
        const sw = createSwitch({
            checked: m.owner || perms.includes(key), disabled: !may, label,
            onChange: async (on) => {
                const before = perms;
                const after = on ? [...perms, key] : perms.filter((p) => p !== key);
                if (on && weighty && !(await glassBlurConfirm(`"${label}" lets ${m.person.username} reshape the space. Delegating this is how a space gets quietly captured. Grant it anyway?`, { title: 'Grant a restricted permission?', okText: 'Grant', danger: true }))) { sw.setChecked(false); return; }
                try { await save({ permissions: after }, true); } catch (err) { perms = before; sw.setChecked(!on); toast(err.message); }
            },
        });
        return h('label', { class: 'sp-perm' }, h('span', {}, label, h('em', { text: weighty ? 'Asks for a confirmation to grant' : 'Freely delegable' })), sw.element);
    });

    const role = may && h('input', { class: 'sp-input', style: 'width:100%', maxlength: 40, value: m.title || '', placeholder: 'Role title, e.g. Sound designer', 'aria-label': 'Role title',
        onchange: async (e) => { const before = title; title = e.target.value; try { await save({ title }); } catch (err) { title = before; e.target.value = before; toast(err.message); } } });

    tablet.append(
        h('div', { class: 'sp-tablet__head' }, face(m.person.username, 'sp-avatar--lg'),
            h('div', {}, h('h3', { class: 'glass-blur-dialog__title' }, h('a', { href: profileHref(m.person.username), text: m.person.username })),
                h('div', { text: [m.title, kind(m)].filter(Boolean).join(' · '), style: 'opacity:.7;font-size:13px' }))),
        role || null, ...rows,
        h('div', { class: 'glass-blur-dialog__actions' },
            space.role !== 'APPLICANT' && !m.owner && !mine && h('button', { class: 'glass-blur-dialog__btn glass-blur-dialog__btn--ghost', type: 'button', text: 'Flag as quiet', onclick: () => { close(); openFlag(space, m, refresh); } }),
            may && !mine && h('button', { class: 'glass-blur-dialog__btn glass-blur-dialog__btn--ghost', type: 'button', text: 'Remove',
                onclick: async () => { try { await spaceMemberRemove(space.id, m.person.username); toast(`${m.person.username} was removed.`); close(); refresh(); } catch (err) { toast(err.message); } } }),
            h('button', { class: 'glass-blur-dialog__btn', type: 'button', onclick: close }, 'Done')));
}
