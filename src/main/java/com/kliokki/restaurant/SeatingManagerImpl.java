package com.kliokki.restaurant;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

public class SeatingManagerImpl implements SeatingManager {

    private final static ReentrantLock queueProcessorLock = new ReentrantLock();
    private final int maxTableSize;
    private final Queue<CustomerGroup> waitingGroups = new ConcurrentLinkedQueue<>();
    private final Map<CustomerGroup, Table> servingGroups = new ConcurrentHashMap<>();
    private final Map<Table, Collection<CustomerGroup>> tablesWithGuests = new HashMap<>();
    private final Map<Integer, Queue<Table>> tablesFreeSeats = new HashMap<>();

    public SeatingManagerImpl(List<Table> tables) {
        maxTableSize = getMaxTableSize(tables);
        populateTablesWithGuests(tables);
        populateTableFreeSeats(tables);
    }

    private int getMaxTableSize(List<Table> tables) {
        int maxSize = 0;
        for(Table table : tables) {
            if (table.size() > maxSize) {
                maxSize = table.size();
                break;
            }
        }
        return maxSize;
    }

    private void populateTablesWithGuests(List<Table> tables) {
        tables.forEach(t -> tablesWithGuests.put(t, new ConcurrentLinkedQueue<>()));
    }

    private void populateTableFreeSeats(List<Table> tables) {
        IntStream.range(0, maxTableSize + 1).forEach(i -> tablesFreeSeats.put(i, new ConcurrentLinkedQueue<>()));
        tables.forEach(t -> tablesFreeSeats.get(t.size()).offer(t));
    }

    @Override
    public void arrives(CustomerGroup newGroup) {
        if (newGroup == null) {
            throw new IllegalArgumentException("newGroup is null");
        }
        boolean newGroupAdded = waitingGroups.add(newGroup); // O(1)
        if (newGroupAdded) {
            processWaitingQueue();
        }
    }

    @Override
    public void leaves(CustomerGroup leavingGroup) {
        if (leavingGroup == null) {
            throw new IllegalArgumentException("leavingGroup is null");
        }
        Table tableToClear = servingGroups.remove(leavingGroup); // O(1)
        if (tableToClear != null) {
            boolean tableCleared = clearTable(tableToClear, leavingGroup);
            if (tableCleared) {
                processWaitingQueue();
            }
        } else {
            waitingGroups.remove(leavingGroup); // O(n)
        }
    }

    private boolean clearTable(Table table, CustomerGroup leavingGroup) {
        Collection<CustomerGroup> tableGroups = tablesWithGuests.get(table); // O(1)
        if (tableGroups != null) {
            tableGroups.remove(leavingGroup); // O(n) but list is very short
        }
        boolean tableCleared = false;
        for(int freeSeats = maxTableSize - leavingGroup.size(); !tableCleared && freeSeats >= 0; freeSeats--) {
            Queue<Table> tables = tablesFreeSeats.get(freeSeats); // O(1)
            if (tables != null) {
                tableCleared = tables.remove(table); // O(n)
            }
            if (tableCleared) {
                tablesFreeSeats.get(freeSeats + leavingGroup.size()).offer(table); //O(1), O(1)
            }
        }
        return tableCleared;
    }

    @Override
    public Table locate(CustomerGroup group) {
        if (group == null) {
            throw new IllegalArgumentException("group is null");
        }
        return servingGroups.get(group); //O(1)
    }

    private void processWaitingQueue() {
        new Thread(this::waitingQueueProcessor).start(); // TODO: subject to refactor
    }

    private void waitingQueueProcessor() {
        if (waitingGroups.isEmpty()) {
            return;
        }

        boolean lockAcquired = queueProcessorLock.tryLock();
        if (!lockAcquired) {
            return;
        }
        final CustomerGroup group = waitingGroups.peek(); // O(1)
        if (group == null) {
            return;
        }

        for (int freeSeats = group.size(); freeSeats <= maxTableSize; freeSeats++) {
            Queue<Table> tablesWithFreeSeats = tablesFreeSeats.get(freeSeats); // O(1)
            if (tablesWithFreeSeats.isEmpty()) {
                continue;
            }
            Table tableWithFreeSeats = tablesWithFreeSeats.poll(); // O(1)
            tablesFreeSeats.get(freeSeats - group.size()).offer(tableWithFreeSeats); // O(1), O(1)
            tablesWithGuests.get(tableWithFreeSeats).add(group); // O(1)

            waitingGroups.poll(); // O(1)
            break;
        }
        queueProcessorLock.unlock(); //TODO: lock is too broad

        waitingQueueProcessor();
    }

}
