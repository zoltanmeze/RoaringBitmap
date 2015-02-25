/*
 * (c) Daniel Lemire, Owen Kaser, Samy Chambi, Jon Alvarado, Rory Graves, Björn Sperber
 * Licensed under the Apache License, Version 2.0.
 */
package org.roaringbitmap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;

/**
 * This container takes the form of runs of consecutive values (effectively,
 * run-length encoding).
 */
public class RunContainer extends Container implements Cloneable, Serializable {
    private static final int DEFAULT_INIT_SIZE = 4;
    short[] valueslength;// first half is used to store the values, second half for the lengths.
    // Lengths are expressed in number of extra repetitions, so 0 means 1 value in the sequence. 
    int nbrruns = 0;
    public String debugString() {// todo: remove
        String ans = "";

        for(int k = 0; k < nbrruns; ++k) {
            ans = ans + "run from "+Util.toIntUnsigned(getValue(k))+" to "+(Util.toIntUnsigned(getValue(k))+Util.toIntUnsigned(getLength(k)));
        }
        return ans;

    }
    private short getValue(int index) {
        return valueslength[index];
    }

    private short getLength(int index) {
        return valueslength[index + valueslength.length / 2];
    }
    
    private void incrementLength(int index) {
        valueslength[index + valueslength.length / 2]++;
    }
    private void decrementLength(int index) {
        valueslength[index + valueslength.length / 2]--;
    }
    
    private void makeRoomAtIndex(int index) {
        if(nbrruns == valueslength.length / 2) increaseCapacity();
        System.arraycopy(valueslength, index  , valueslength, index + 1 , nbrruns - index );
        System.arraycopy(valueslength, index  + valueslength.length / 2, valueslength, index + 1 + valueslength.length / 2, nbrruns - index );
        nbrruns++;
    }

    private void recoverRoomAtIndex(int index) {
        System.arraycopy(valueslength, index + 1 , valueslength, index  , nbrruns - index - 1 );
        System.arraycopy(valueslength, index + 1 + valueslength.length / 2, valueslength, index  + valueslength.length / 2, nbrruns - index - 1);
        nbrruns--;
    }
    
    private RunContainer(int nbrruns, short[] valueslength) {
        this.nbrruns = nbrruns;
        this.valueslength = Arrays.copyOf(valueslength, valueslength.length);
    }


    private void increaseCapacity() {
        int newCapacity = (valueslength.length == 0) ? DEFAULT_INIT_SIZE : valueslength.length < 64 ? valueslength.length * 2
                : valueslength.length < 1024 ? valueslength.length * 3 / 2
                : valueslength.length * 5 / 4;
        short[] nv = new short[newCapacity];
        System.arraycopy(valueslength, 0, nv, 0, nbrruns);
        System.arraycopy(valueslength, valueslength.length / 2, nv, nv.length / 2, nbrruns);
        valueslength = nv;
    }

    
    /**
     * Create a container with default capacity
     */
    public RunContainer() {
        this(DEFAULT_INIT_SIZE);
    }

    /**
     * Create an array container with specified capacity
     *
     * @param capacity The capacity of the container
     */
    public RunContainer(final int capacity) {
        valueslength = new short[2 * capacity];
    }

    
    @Override
    public Iterator<Short> iterator() {
        return new Iterator<Short>() {
            int pos = 0;
            int le = 0;


            @Override
            public boolean hasNext() {
                return (pos < RunContainer.this.nbrruns) && (le <= Util.toIntUnsigned(RunContainer.this.getLength(pos)));
            }

            @Override
            public Short next() {
                short ans = (short) (RunContainer.this.valueslength[pos] + le);
                le++;
                if(le > Util.toIntUnsigned(RunContainer.this.getLength(pos))) {
                    pos++;
                    le = 0;
                }
                return ans;
            }

            @Override
            public void remove() {
                throw new RuntimeException("Not implemented");// TODO
            }
        };

    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        serialize(out);
        
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        deserialize(in);
        
    }

    @Override
    public Container add(short k) {
        int index = Util.unsignedBinarySearch(valueslength, 0, nbrruns, k);
        if(index >= 0) return this;// already there
        index = - index - 2;// points to preceding value, possibly -1
        if((index >= 0) && (index < nbrruns)) {// possible match
            int offset = Util.toIntUnsigned(k) - Util.toIntUnsigned(getValue(index));
            int le =     Util.toIntUnsigned(getLength(index)); 
            if(offset <= le) return this;
            if(offset == le + 1) {
                // we may need to fuse
                if(index + 1 < nbrruns ) {
                    if(Util.toIntUnsigned(getValue(index + 1))  == Util.toIntUnsigned(k) + 1) {
                        // indeed fusion is needed
                        recoverRoomAtIndex(index + 1);
                        valueslength[index +  valueslength.length / 2] = (short) (getValue(index + 1) + getLength(index + 1) - getValue(index));
                        return this;
                    }
                }
                incrementLength(index);
                return this;
            }
        }
        if( index == -1) {
            // we may need to extend the first run
            if(0 < nbrruns ) {
                if(getValue(0)  == k + 1) {
                    valueslength[ valueslength.length / 2]++;
                    valueslength[0]--;
                    return this;
                }
            }
        }
        makeRoomAtIndex(index + 1);
        valueslength[index + 1] = k;
        valueslength[index + 1 + valueslength.length / 2] = 0;
        return this;
    }

    @Override
    public Container and(ArrayContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container and(BitmapContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container andNot(ArrayContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container andNot(BitmapContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void clear() {
        nbrruns = 0;
    }

    @Override
    public Container clone() {
        return new RunContainer(nbrruns, valueslength);
    }

    @Override
    public boolean contains(short x) {
        int index = Util.unsignedBinarySearch(valueslength, 0, nbrruns, x);
        if(index >= 0) return true;
        index = - index - 2; // points to preceding value, possibly -1
        if((index >= 0) && (index < nbrruns)) {// possible match
            int offset = Util.toIntUnsigned(x) - Util.toIntUnsigned(getValue(index));
            int le =     Util.toIntUnsigned(getLength(index)); 
            if(offset <= le) return true;
        }
        return false;
    }

    @Override
    public void deserialize(DataInput in) throws IOException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void fillLeastSignificant16bits(int[] x, int i, int mask) {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected int getArraySizeInBytes() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getCardinality() {
        int sum = 0;
        for(int k = 0; k < nbrruns; ++k)
            sum = sum + Util.toIntUnsigned(getLength(k)) + 1;
        return sum;
    }

    @Override
    public ShortIterator getShortIterator() {
        return new ShortIterator() {
            int pos = 0;
            int le = 0;


            @Override
            public boolean hasNext() {
                return (pos < RunContainer.this.nbrruns) && (le <= Util.toIntUnsigned(RunContainer.this.getLength(pos)));
            }
            
            
            @Override
            public ShortIterator clone() {
                try {
                    return (ShortIterator) super.clone();
                } catch (CloneNotSupportedException e) {
                    return null;// will not happen
                }
            }


            @Override
            public short next() {
                short ans = (short) (RunContainer.this.valueslength[pos] + le);
                le++;
                if(le > Util.toIntUnsigned(RunContainer.this.getLength(pos))) {
                    pos++;
                    le = 0;
                }
                return ans;
            }

            @Override
            public void remove() {
                throw new RuntimeException("Not implemented");// TODO
            }
        };
    }

    @Override
    public ShortIterator getReverseShortIterator() {
        return new ShortIterator() {
            int pos = nbrruns - 1;
            int le = 0;


            @Override
            public boolean hasNext() {
                return (pos >= 0) && (le <= Util.toIntUnsigned(RunContainer.this.getLength(pos)));
            }
            
            
            @Override
            public ShortIterator clone() {
                try {
                    return (ShortIterator) super.clone();
                } catch (CloneNotSupportedException e) {
                    return null;// will not happen
                }
            }


            @Override
            public short next() {
                short ans = (short) (RunContainer.this.valueslength[pos] + Util.toIntUnsigned(RunContainer.this.getLength(pos)) - le);
                le++;
                if(le > Util.toIntUnsigned(RunContainer.this.getLength(pos))) {
                    pos--;
                    le = 0;
                }
                return ans;
            }

            @Override
            public void remove() {
                throw new RuntimeException("Not implemented");// TODO
            }
        };

    }

    @Override
    public int getSizeInBytes() {
        return this.nbrruns * 4 + 4;
    }

    @Override
    public Container iand(ArrayContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container iand(BitmapContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container iandNot(ArrayContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container iandNot(BitmapContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container inot(int rangeStart, int rangeEnd) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container ior(ArrayContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container ior(BitmapContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container ixor(ArrayContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container ixor(BitmapContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container not(int rangeStart, int rangeEnd) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container or(ArrayContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container or(BitmapContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container remove(short x) {
        int index = Util.unsignedBinarySearch(valueslength, 0, nbrruns, x);
        if(index >= 0) {
            int le =  Util.toIntUnsigned(getLength(index));
            if(le == 0) {
                recoverRoomAtIndex(index);
            } else {
                valueslength[index]++;
                decrementLength(index);
            }
            return this;// already there
        }
        index = - index - 2;// points to preceding value, possibly -1
        if((index >= 0) && (index < nbrruns)) {// possible match
            int offset = Util.toIntUnsigned(x) - Util.toIntUnsigned(getValue(index));
            int le =     Util.toIntUnsigned(getLength(index)); 
            if(offset < le) {
                // need to break in two
                int currentlength  = Util.toIntUnsigned(valueslength[index + valueslength.length / 2]);
                valueslength[index + valueslength.length / 2]  = (short) (offset - 1);
                // need to insert
                int newvalue = Util.toIntUnsigned(x) + 1;
                int newlength = currentlength  - offset - 1;
                makeRoomAtIndex(index+1);
                valueslength[index + 1 ] = (short) newvalue;
                valueslength[index + valueslength.length / 2  + 1 ] = (short) newlength;
            } else if(offset == le ) {
                decrementLength(index);
            }
        }
        // no match
        return this;
    }

    @Override
    public void serialize(DataOutput out) throws IOException {
        writeArray(out);
    }

    @Override
    public int serializedSizeInBytes() {
        return 2 + 2 * nbrruns;
    }

    @Override
    public void trim() {
        if(valueslength.length == 2* nbrruns) return;
        int newCapacity = 2 * nbrruns;
        short[] nv = new short[newCapacity];
        System.arraycopy(valueslength, 0, nv, 0, nbrruns);
        System.arraycopy(valueslength, valueslength.length / 2, nv, nv.length / 2, nbrruns);
        valueslength = nv;        
    }

    @Override
    protected void writeArray(DataOutput out) throws IOException {
        out.write((this.nbrruns) & 0xFF);
        out.write((this.nbrruns >>> 8) & 0xFF);
        for (int k = 0; k < this.nbrruns; ++k) {
            out.write((this.valueslength[k]) & 0xFF);
            out.write((this.valueslength[k] >>> 8) & 0xFF);
        }
        for (int k = 0; k < this.nbrruns; ++k) {
            out.write((this.valueslength[k + valueslength.length / 2]) & 0xFF);
            out.write((this.valueslength[k + valueslength.length / 2] >>> 8) & 0xFF);
        }
    }

    @Override
    public Container xor(ArrayContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Container xor(BitmapContainer x) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int rank(short lowbits) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public short select(int j) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Container limit(int maxcardinality) {
        // TODO Auto-generated method stub
        return null;
    }
    

    @Override
    public boolean equals(Object o) {
        if (o instanceof RunContainer) {
            RunContainer srb = (RunContainer) o;
            if (srb.nbrruns != this.nbrruns)
                return false;
            for (int i = 0; i < nbrruns; ++i) {
                if (this.getValue(i) != srb.getValue(i))
                    return false;
                if (this.getLength(i) != srb.getLength(i))
                    return false;
            }
            return true;
        }
        return false;
    }

}
