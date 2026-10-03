// Who a notification is about, for the pictures that go with it: the person whose name opens the line, and (for a removal of someone else) the one removed too.
const ABOUT_SOMEONE = /^(New (follower|comment|reply|application|like)|Application withdrawn|Disagreed with your selection|You were asked to collaborate|Collaborator (accepted|declined)|A collaborator stepped down|You were removed as a collaborator|You were removed from the space|A member left|Someone was removed)$/;

export function whoIn(n) {
    if (!ABOUT_SOMEONE.test(n.title) || !n.body) return [];
    const w = n.body.split(' ');
    return n.title === 'Someone was removed' ? [w[0], w[2]] : [w[0]];   // "<a> removed <b> from ..."
}
