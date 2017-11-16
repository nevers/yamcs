package org.yamcs.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.yamcs.utils.TimeEncoding;

/**
 *  time interval where both ends can be open 
 *  
 **/
public class TimeInterval {
    private long start, stop;
    private boolean hasStart = false;
    private boolean hasStop = false;

    public TimeInterval(long start, long stop) {
        setStart(start);
        setStop(stop);
    }
    /**
     * Creates a TimeInterval with no start and no stop
     */
    public TimeInterval() {
    }
    /**
     * creates a TimeInterval with no start but with an stop
     */
    public static TimeInterval openStart(long stop) {
        TimeInterval ti = new TimeInterval();
        ti.setStop(stop);
        return ti;
    }

    public boolean hasStart() {
        return hasStart;
    }

    public boolean hasStop() {
        return hasStop;
    }
    public void setStart(long start) {
        hasStart = true;
        this.start = start;
    }

    public long getStart() {
        return start;
    }

    public void setStop(long stop) {
        hasStop = true;
        this.stop = stop;
    }

    public long getStop() {
        return stop;
    }
    
    /**
     * Checks that [this.start, this.stop) contains t
     */
    boolean contains0(long t) {
        return !((hasStart && t<start) || (hasStop && t>=stop)); 
    }
    
    /**
     * Checks that [this.start, this.stop] overlaps with [t1.start, t1.stop)
     * 
     */
    boolean overlaps1(TimeInterval t1) {
        return !((t1.hasStart && hasStop && t1.start>stop) ||
                 (t1.hasStop && hasStart && start>=t1.stop)); 
    }


    @Override
    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append("(");
        if(hasStart) {
            sb.append(start);
        }
        sb.append(",");
        if(hasStop) {
            sb.append(stop);
        }
        sb.append(")");
        return sb.toString();
    }

    public String toStringEncoded() {
        StringBuilder sb=new StringBuilder();
        sb.append("(");
        if(hasStart) {
            sb.append(TimeEncoding.toString(start));
        }
        sb.append(",");
        if(hasStop) {
            sb.append(TimeEncoding.toString(stop));
        }
        sb.append(")");
        return sb.toString();
    }


    /**
     * Filters an input iterator to the intervals that match the given timeInterval
     *
     */
    public static class FilterOverlappingIterator<T extends TimeInterval> implements Iterator<T> {
        TimeInterval timeInterval;
        T next;
        Iterator<T> it;

        /**
         * Creates a new Interator that iterates the elements of inputIterator and outputs only those 
         * that overalp with timeInterval.
         * 
         * The timeInterval is considered closed at both ends [start, stop] whereas the elements of the 
         * inputIterator are considered closed at start but open at stop [start, stop)
         * 
         * The inputIterator is assumed to contain elements sorted by the start. 
         * 
         */
        public FilterOverlappingIterator(TimeInterval timeInterval, Iterator<T> inputIterator) {
            this.timeInterval = timeInterval;
            this.it = inputIterator;
            while(it.hasNext()) {
                T n = it.next();
                if(timeInterval.overlaps1(n)) {
                    next = n;
                    break;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public T next() {
            if (next==null) {
                throw new NoSuchElementException();
            }
            T r = next;
            getNext();
            return r;
        }

        private void getNext() {
            if (it.hasNext()) {
                next = it.next();
                if(timeInterval.hasStop() && next.hasStart() && timeInterval.getStop()<next.getStart()) {
                    next = null;
                }
            } else {
                next = null;
            }
        }
    }
}
