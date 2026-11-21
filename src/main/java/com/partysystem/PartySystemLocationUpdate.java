package com.partysystem;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Broadcast to the party whenever the local player's world or tile changes.
 * world == 0 means the sender is logged out.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class PartySystemLocationUpdate extends PartyMemberMessage
{
	String name;
	int world;
	int x;
	int y;
	int plane;
	boolean instanced;
}
