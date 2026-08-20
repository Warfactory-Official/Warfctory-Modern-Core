package com.norwood.wfcore.common.chat;

/** What the chat blacklist does with a message that contains a blacklisted word. */
public enum FilterAction {
    /** Cancel the message entirely so nobody sees it. */
    BLOCK,
    /** Let the message through, but replace each blacklisted hit with censor characters. */
    CENSOR
}
