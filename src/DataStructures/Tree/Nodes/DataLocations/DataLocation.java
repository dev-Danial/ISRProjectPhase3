package DataStructures.Tree.Nodes.DataLocations;

class DataLocation<T>
{
    private T node;
    private int offset;

    public DataLocation(T node, int offset)
    {
        this.node = node;
        this.offset = offset;
    }

    public T getNode()
    {
        return node;
    }

    public void setNode(T node)
    {
        this.node = node;
    }

    public int getOffset()
    {
        return offset;
    }

    public void setOffset(int offset)
    {
        this.offset = offset;
    }
}