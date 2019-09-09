package src;
import com.google.common.collect.BoundType;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import javolution.util.FastSortedMap;
import javolution.util.FastTable;
import javolution.util.FastSortedTable;
import org.javatuples.Quartet;
import java.util.Queue;
import org.javatuples.Pair;
import com.google.common.collect.Range;
import javolution.io.Struct;

class MetaData<L, C extends Comparable<C>> extends Struct {
    public int level; //"inception level", i.e. root list = 1, root list's branch = 2, root list's branch's branch = 3 ...
    public Pair<Integer, Boolean> splitCapacity; //the maximum allowed number of splits along this.list, and whether capacity is enforced
    public Pair<Integer, Boolean> branchCapacity; //the maximum allowed number of branches per split along this.list, and whether capacity is enforced
    public BranchingRangeList<L, C> parent; //copy reference to the list that this.list branched off from
    public Range<C> range; //full range represented by this.list by ignoring any splits
}

public class BranchingRangeList <L, C extends Comparable<C>> implements Cloneable {
    //CONSTRUCTORS
    BranchingRangeList(C lower, BoundType lowerType, C upper, BoundType upperType){
        this.metadata.level = this.getParent().getLevel() + 1;
        this.metadata.range = Range.range(lower, lowerType, upper, upperType);
    }

    BranchingRangeList(Range<C> range){
        this(range.lowerEndpoint(), range.lowerBoundType(), range.upperEndpoint(), range.upperBoundType());
    }

    //ACCESSORS
    public int getSplitCapacity(){
        return this.metadata.splitCapacity.getValue0();
    }
    public int getBranchCapacity() {
        return this.metadata.branchCapacity.getValue0();
    }
    public boolean hasSplitCapacity(){
        return this.metadata.splitCapacity.getValue1();
    }
    public boolean hasBranchCapacity(){
        return this.metadata.branchCapacity.getValue1();
    }
    public int getLevel(){
        return this.metadata.level;
    }
    public BranchingRangeList<L, C> getParent(){
        return this.metadata.parent;
    }
    public Range<C> getRange(){
        return this.metadata.range;
    }

    //counts number of elements in this.list, e.g. number of discrete pieces of this.getRange due to branching
    public int getSize(){
        return this.list.size();
    }

    //counts number of times this.getRange has been split
    public int getSplitsSize(){
        return this.getSize() - 1;
    }

    //counts the number of splits with no branches with length > 0
    public int getEmptySplitSize(){
        return (int)this.list.stream()
                .filter(n1 -> (
                        n1.getValue3().size() == 0 || n1.getValue3().stream().allMatch(n2 -> n2.getSize() == 0)))
                .count();
    }

    //counts number of branches total that stem from this.list only, therefore any branches with level > this.metadata.level + 1 are omitted
    public int getBranchesSize(){
        return this.list.stream().
                mapToInt(n1 -> (int)n1
                        .getValue3()
                        .stream()
                        .filter(n2 -> n2.list.size() > 0)
                        .count())
                .sum();
    }

    //counts the number of branches with length > 0 at split at i^th element in this.list
    public int getBranchesAtSplit(int i){
        return (int)this.list.get(i - 1).getValue3().stream().filter(n2 -> n2.list.size() > 0).count();
    }

    public void traverseByBreadth(){
        this.traverseByBreadth_Array();
        for(BranchingRangeList<L, C> brl : this.visitedRanges)
            System.out.println(brl);
    }
    public void traverseByBreadth_Array(){
        this.visitedRanges.clear();
        Queue<BranchingRangeList<L, C>> Q = new LinkedBlockingQueue<>();
        Q.offer(this);
        BranchingRangeList<L, C> top = null;
        while(!Q.isEmpty()){
            top = Q.poll();
            for(var table : top.list.stream().filter(n1 -> n1.getValue3().size() > 0).map(Quartet::getValue3).collect(Collectors.toUnmodifiableList()))
                for(var brl : table.stream().filter(n3 -> n3.getSize() > 0).collect(Collectors.toUnmodifiableList()))
                    Q.offer(brl);
            this.visitedRanges.add(top);
        }
    }

    //MUTATORS
    public void setID(L identifier){
        this.id = identifier;
    }

    //sets this.metadata.splitCapacity to given value and enforces the use of such capacity (true)
    public boolean setSplitCapacity(int cap){
        if(cap < 0)
            return false;
        this.metadata.splitCapacity.setAt0(cap);
        this.metadata.splitCapacity.setAt1(true);
        this.resizeSplit(cap);
        return true;
    }

    //sets this.metadata.branchCapacity to given value and enforces the use of such capacity (true)
    public boolean setBranchCapacity(int cap){
        if(cap < 0)
            return false;
        this.metadata.branchCapacity.setAt0(cap);
        this.metadata.branchCapacity.setAt1(true);
        this.resizeBranchAll(cap, true);
        return true;
    }
    public void setWeight(int i, double w){
        this.list.get(i - 1).setAt1(w);
    }

    //resizes number of elements in this.list, thus possibly reducing how many splits there are
    public boolean resizeSplit(int size){
        if(size < 0 || size > this.getSplitCapacity() && this.hasSplitCapacity())
            return false;
        if(size < this.getSplitsSize())
            for(int i = this.getSize() - 1; i >= size; i--)
                this.mergeNextRange(i);
        return true;
    }

    //resize the number of branches sprouting off the i^th element in this.list, and removes the shortest/longest branches first if size < no. of branches
    public boolean resizeBranch(int i, int size, boolean removeLongerBranches){
        if(i <= 0 || size < 0)
            return false;
        if(size < this.list.get(i - 1).getValue3().size())
            if(removeLongerBranches)
                for(int pos =  this.list.get(i - 1).getValue3().size() - 1; i >= size; i--)
                    this.mergeNextRange(pos);
            else
                while(this.list.get(i - 1).getValue3().size() > size)
                    this.mergeNextRange(1);
        return true;
    }

    //resize the number of branches to the same size for all elements in this.list, and removes the shortest/longest branches first if size < no. of branches
    public boolean resizeBranchAll(int size, boolean removeLongerBranches){
        if(size < 0)
            return false;
        for(int i = 1; i <= this.getSize(); i++)
            this.resizeBranch(i, size, removeLongerBranches);
        return true;
    }

    //resizes this.metadata.range and trims off elements in this.list if needed
    public boolean resizeRange(C lower, C upper){
        if((double)lower > (double)upper)
            return false;
        BoundType lowerType = this.list.getFirst().getValue0().lowerBoundType(), upperType = this.list.getLast().getValue0().upperBoundType();
        int pos1 = this.list.indexOf(this.list.stream().filter(n -> n.getValue0().contains(lower)).collect(Collectors.toList()).get(0)) + 1;
        int pos2 = this.list.indexOf(this.list.stream().filter(n -> n.getValue0().contains(upper)).collect(Collectors.toList()).get(0)) + 1;
        if(pos1 <= 0 && pos2 <= 0)
            return false;
        if(pos1 > 0)
            for (int i = 1; i < pos1; i++)
                this.list.remove(i - 1);
        if(pos2 > 0)
            for(int i = this.getSize(); i > pos2; i--)
                this.list.remove(i - 1);
        if(pos1 == pos2) {
            this.list.getFirst().setAt0(Range.range(lower, lowerType, upper, upperType));
            return true;
        }
        if(pos1 > 0)
            this.list.getFirst().setAt0(Range.range(lower, lowerType, this.list.getFirst().getValue0().upperEndpoint(), BoundType.CLOSED));
        if(pos2 > 0)
            this.list.getLast().setAt0(Range.range(this.list.getLast().getValue0().lowerEndpoint(), BoundType.CLOSED, upper, upperType));
        return true;
    }

    //puts a new attribute to i^th element; a replacement is done if a key already exists
    public <T> boolean putAttribute(int i, String key, T attribute){
        if(i <= 0 || i > this.list.size())
            return false;
        this.list.get(i - 1).getValue1().put(key, new Attribute(attribute));
        return true;
    }

    //puts attribute with same key and value pair across all elements of this.list
    public <T> void putAttributeAll(String key, T attribute){
        for(int i = 1; i <= this.getSize(); i++)
            this.putAttribute(i - 1, key, attribute);
    }

    //adds new BranchingRangeList object by branching off of this.list
    public boolean addBranch(Range<C> range){
        Range<C> temp = Range.range(range.lowerEndpoint(), BoundType.CLOSED, range.upperEndpoint(), range.upperBoundType());
        if(!this.metadata.range.encloses(temp))
            return false;
        int pos = this.list.indexOf(this.list.stream()
                .filter(
                        n -> n.getValue0()
                        .contains(temp.lowerEndpoint()))
                .collect(Collectors.toUnmodifiableList()).get(0))
                + 1;
        if(this.list.get(pos - 1).getValue0().upperEndpoint().equals(temp.lowerEndpoint()))
            this.list.get(pos - 1).getValue3().add(new BranchingRangeList<>(temp));
        else
            this.splitRange(pos, temp.lowerEndpoint());
        return true;
    }

    //adds branch that starts at the i^th element's upperEndpoint and ends (closed) at a given value
    public boolean addBranch(int i, C upperEndpoint){
        C lowerEndpoint = this.list.get(i - 1).getValue0().upperEndpoint();
        if(i <= 0 || !this.getRange().contains(lowerEndpoint) || (i == this.getSize() && this.getRange().upperBoundType().equals(BoundType.OPEN)))
            return false;
        this.list.get(i - 1).getValue3().add(new BranchingRangeList<>(lowerEndpoint, BoundType.CLOSED, upperEndpoint, BoundType.CLOSED));
        return true;
    }

    //merges the i^th element of list (index = i - 1) with j^th element of list and removes all branches and attributes in between
    //except for the element with larger index
    public boolean mergeRange(int i, int j){
        if(i <= 0 || j <= 0 || i > this.list.size() || j > this.list.size() || i == j)
            return false;
        int pos1 = Math.min(i, j), pos2 = Math.min(i, j);
        C lowerBound = null, upperBound = null;
        this.list.get(pos1 - 1).setAt0(this.list.get(pos1 - 1).getValue0().span(this.list.get(pos2 - 1).getValue0()));
        this.list.get(pos1 - 1).setAt1(this.list.get(pos2 - 1).getValue1());
        this.list.get(pos1 - 1).setAt2(this.list.get(pos2 - 1).getValue2());
        this.list.get(pos1 - 1).setAt3(this.list.get(pos2 - 1).getValue3());
        for(int pos = pos1 + 1; pos <= pos2; pos++)
            this.list.remove(pos - 1);
        return true;
    }

    //applies mergeRange function to i^th element in this.list with its right neighbor
    public boolean mergeNextRange(int i){
        return this.mergeRange(i, i + 1);
    }

    //splits i^th element in this.list into 2 new elements, and dividing the previous i^th range into 2 parts by some contained value
    public boolean splitRange(int i, C cut){
        if(i <= 0 || i > this.getSize() || !this.list.get(i - 1).getValue0().contains(cut))
            return false;
        var temp = new Quartet<>(
                Range.range(cut, BoundType.CLOSED, this.list.get(i - 1).getValue0().upperEndpoint(), this.list.get(i - 1).getValue0().upperBoundType()),
                this.list.get(i - 1).getValue1(),
                this.list.get(i - 1).getValue2(),
                this.list.get(i - 1).getValue3());
        this.list.add(i, temp);
        this.list.get(i - 1).setAt0(Range.closed(this.list.get(i - 1).getValue0().lowerEndpoint(), cut));
        this.list.get(i - 1).getValue3().clear();
        return true;
    }

    //MEMBER VARIABLES
    private L id = null;
    private MetaData<L, C> metadata = new MetaData<>();
    private FastTable<BranchingRangeList<L, C>> visitedRanges = new FastTable<>();
    private FastTable<Quartet<Range<C>, FastSortedMap<String, Attribute>, Double, FastSortedTable<BranchingRangeList<L, C>>>> list = new FastTable<>();

}