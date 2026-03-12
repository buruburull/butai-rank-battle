package com.butai.rankbattle.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Team {

    private int teamId;
    private String name;
    private UUID leaderId;
    private final Set<UUID> members;

    public Team(int teamId, String name, UUID leaderId) {
        this.teamId = teamId;
        this.name = name;
        this.leaderId = leaderId;
        this.members = new HashSet<>();
        this.members.add(leaderId);
    }

    public int getTeamId() {
        return teamId;
    }

    public void setTeamId(int teamId) {
        this.teamId = teamId;
    }

    public String getName() {
        return name;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(UUID leaderId) {
        this.leaderId = leaderId;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leaderId.equals(uuid);
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public int getMemberCount() {
        return members.size();
    }
}
