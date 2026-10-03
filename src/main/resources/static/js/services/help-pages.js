// The words of the in-app Help (help.js): for each page, a few cards. To add a page, add one entry; the key is the page's file name.
//   key: { title, steps: [[heading, text], ...] }
export const HELP = {
    gaze: { title: 'The Gaze', steps: [
        ['The Gaze: everyone\'s ideas', 'The newest ideas from everyone, newest first. Pull down to refresh. Swipe sideways, or tap the names at the top, to move between The Gaze, Shared and Open.'],
        ['Shared', 'Ideas from the people in your network: the ones you follow, and what they have shouted out.'],
        ['Open', 'Ideas that are still taking applications, so you can find a team to join.'],
        ['Search', 'The magnifier at the top finds ideas and people. Tap a person\'s picture to open their profile.'],
        ['On an idea', 'Like it, comment, shout it out to your followers, copy its link or share it by yarn. To join, press Apply and write a few lines on why you fit.'],
        ['Have an idea?', 'Choose New post from the shortcuts (hold the round button) or the menu.'],
    ] },
    yarnspaces: { title: 'Yarns', steps: [
        ['Yarn is our word for message', 'Swipe sideways, or tap the names at the top, to move between All yarns, MySpaces, WeSpaces and WorkSpaces. Pinned and unread ones rise to the top.'],
        ['All yarns', 'Every conversation you are in, in one list.'],
        ['MySpaces', 'Private chats, one to one. Start one from someone\'s profile with Message.'],
        ['WeSpaces', 'Co-founders deciding together before the team is formed.'],
        ['WorkSpaces', 'The team building the idea. Open one for the Room, Members, Milestones, Payments and Settings.'],
        ['Inside a chat', 'The (i) at the top has that chat\'s settings. Write at the bottom and press send; the ticks show it was delivered, and green means everyone has read it.'],
        ['Archive and Blocklist', 'Both live in Settings: Yarns for the Archive, Security and privacy for the Blocklist and who may message you.'],
    ] },
    space: { title: 'A space', steps: [
        ['Where an idea becomes a team', 'A space is made when the founder accepts people. Accepted applicants read everything about the idea first; the chats open only when they press Join space.'],
        ['Inside', 'The people, the tasks, the milestones and the payments for this idea are all here, so the team keeps one record of who agreed to what.'],
        ['Talk', 'The chat for this space is in Yarns, under MySpaces, WeSpaces or WorkSpaces.'],
    ] },
    wespace: { title: 'About the group', steps: [
        ['About', 'The idea and the way in.'],
        ['Collaborators', 'The seats: who holds one and who has been invited.'],
        ['Quiet', 'The response clock and any collaborators who have been flagged for going quiet. Only shown while you are not frozen out.'],
    ] },
    workspace: { title: 'The Workspace', steps: () => {
        const cards = {
            room: ['Room', 'The team\'s chat. Pinned notes stay on top, and the Tasks bar under the header opens the open tasks.'],
            members: ['Members', 'Everyone on the team with their role. Open a member to see their profile and permissions; frozen members are marked.'],
            milestones: ['Milestones', 'Log what was delivered so the whole team sees the same record.'],
            payments: ['Payments', 'Log what was paid and to whom, so nobody has to remember.'],
            settings: ['Settings', 'The response clock (how long a quiet member has to answer a nudge), Pleas, and the space\'s size. Every change is announced in the room.'],
        };
        const now = Object.keys(cards).find((k) => location.hash.endsWith('#' + k)) || 'room';
        return [
            ['Where the team builds the MVP', 'Swipe sideways, or tap the names under the header, to move between Room, Members, Milestones, Payments and Settings. The faces beside the name, and the (i) at the top, open Members.'],
            cards[now], ...Object.entries(cards).filter(([k]) => k !== now).map(([, c]) => c),
        ];
    } },
    'create-post': { title: 'New post', steps: [
        ['Write the idea', 'Say what you are building and who you need. Add pictures or videos, and tags with a #.'],
        ['Post settings', 'The button under the writing sets who can see it and what is allowed on it (comments, shout-outs, anonymous), for this post only. Your defaults live in Settings, under New posts.'],
        ['Save or publish', 'Save draft keeps it for later (find it on your profile). Leaving with unsaved writing asks you first.'],
    ] },
    'view-post': { title: 'A post', steps: [
        ['One idea, with its comments', 'You are here from a card in the Gaze, a copied link, or a post someone yarned to you.'],
        ['Join in', 'Comment at the bottom, like it, shout it out, or Apply if it is taking applications.'],
    ] },
    profile: { title: 'Profile', steps: [
        ['A person', 'Their picture, bio, links and who follows them. Follow or Message them from here. On your own, edit your profile and open Settings with the gear.'],
        ['Posts', 'What they have written. Sort by Latest, Popular or Oldest.'],
        ['Shout-outs', 'Ideas they passed on to their followers.'],
        ['Removals', 'The record of posts that were taken down.'],
        ['Credentials', 'Earned when someone forms a space or hits a milestone. Their owner chooses who sees them.'],
        ['Feats', 'The credentials they chose to show off. On your own profile, open a credential and press Make a feat.'],
            ] },
    'profile-settings': { title: 'Settings', steps: [
        ['Settings, in segments', 'Each card is a segment, and the title at the top names the one you are in. Appearance, Yarns, New posts and Security are saved in turn as you change them.'],
        ['Appearance and themes', 'Theme: Dusk or Light. Navigation preference: the round button at the bottom, or a ‹ and a menu in every header. Display size: a slider that makes text and icons bigger or smaller, with a sample under it; let go to keep it. These three are saved on your account, so every device you sign in on looks the same.'],
        ['Yarns', 'Your default chat, and the Archive of chats you put away.'],
        ['New posts', 'What every new post starts from: who can see it, and what is allowed on it. On the Create post page you can change it for just that post. These stay on this device.'],
        ['Security and privacy', 'Who can start a chat with you, the Blocklist, your History of ideas seen, and your password and sign-in.'],
        ['Sign out', 'The button at the bottom signs you out of this device only.'],
    ] },
    history: { title: 'History', steps: [
        ['Ideas you have seen', 'Kept on this device only. Search them by their words or by username.'],
        ['Tidy up', 'Remove one, or clear them all.'],
    ] },
    notifications: { title: 'Notifications', steps: [
        ['Everything that happened', 'Swipe sideways, or tap the names at the top, to move between All, Spaces, Activity and Personal.'],
        ['Spaces', 'Notes about the spaces you are in.'],
        ['Activity', 'What other people did with your posts and your profile.'],
        ['Personal', 'Notes addressed to you.'],
        ['Find and clear', 'Search the list. The funnel shows unread only, or marks everything read. Tap one to go to where it happened.'],
    ] },
    connections: { title: 'Connections', steps: [
        ['Following, Followers and MyGuy', 'MyGuy is people who follow each other.'],
        ['Search', 'Type letters in any order to find someone. Tap a person to open their profile.'],
    ] },
    applicants: { title: 'Review applicants', steps: [
        ['Your idea\'s applicants', 'Pending, Shortlisted, Accepted and Declined are the tabs. Read each why-you and their credentials.'],
        ['Decide by hand', 'Accept, Decline, or Shortlist to set someone aside for now. The funnel sorts them.'],
        ['Form the space', 'When you are ready, forming the space is the step at the top. Accepted people then choose to join.'],
    ] },
    applications: { title: 'My applications', steps: [
        ['Where each one stands', 'Pending, Shortlisted, Accepted and Closed.'],
        ['Accepted?', 'Tap it to read about the idea and join the space.'],
        ['Changed your mind?', 'A pending application can be withdrawn.'],
    ] },
    report: { title: 'Report a problem', steps: [
        ['Show us what went wrong', 'Attach a screenshot or a short video, and write a few lines. It goes straight to the people who fix things.'],
        ['Leaving', 'If you have started writing, leaving asks first.'],
    ] },
};

/** The last card, on every page: how to get around. Depends on the navigation preference. */
export const AROUND = {
    'omni-wheel': [
        ['Getting around: the round button', 'Tap it to open the bar: Gaze, Yarns, Profile, Settings and Help. Slide along it to pick one, or tap outside to put it away. The bottom of the screen blurs so it is always readable.'],
        ['Shortcuts: hold it', 'Hold the round button and a ring of shortcuts opens: Home, Report a problem, New post, Back and Profile. Slide to one and let go. The page you are on is left out, and Help takes the middle.'],
    ],
    'return': [
        ['Getting around: the ‹', 'The ‹ at the top left of every page goes back to where you came from. If you have unsaved writing, it asks first.'],
        ['The four squares', 'The four squares at the top right open the menu of main pages: Gaze, Yarns, Notifications, Post, Profile, Settings, Report a problem and Help. A dot on it means something new.'],
    ],
};
// Choose the Navigation preference in Settings > Appearance and themes: the round button, or a ‹ and a menu in every header.
