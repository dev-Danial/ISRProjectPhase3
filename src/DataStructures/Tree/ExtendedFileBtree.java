package DataStructures.Tree;

import DataStructures.Tree.Nodes.DataLocations.FileDataLocation;
import DataStructures.Tree.Nodes.DataLocations.RamDataLocation;
import DataStructures.Tree.Nodes.FileNode;
import DataStructures.Tree.Nodes.RamFileNode;
import Primitives.Interfaces.Parsable;
import Primitives.Interfaces.Sizeofable;
import javafx.util.Pair;

import java.util.HashMap;
import java.util.Vector;

public class ExtendedFileBtree<Value extends Sizeofable & Parsable> extends FileBtreeTemplate<Value>
{
    private HashMap<Long, RootInfo> roots;
    private RamFileBtree<Value> ramFileBtree;

    public ExtendedFileBtree(int keyMaxSize, int valueMaxSize, int halfMaxSize, Class valueClassType, RamFileBtree<Value> ramFileBtree, int fileID)
    {
        super(keyMaxSize, valueMaxSize, halfMaxSize, valueClassType, fileID);
        roots = new HashMap<>();
        this.ramFileBtree = ramFileBtree;
    }

    @Override
    public Value search(String key)
    {
        return null;
    }

    public Value search(String key, Long startingNode)
    {
        FileNode<Value> rootNodeTemplate = getNode(startingNode);
        if (rootNodeTemplate.getSize() == 0)
            return null;
        FileDataLocation<Value> loc = findLoc(key, rootNodeTemplate);
        if (!thisDataExists(key, loc))
            return null;
        return loc.getNode().getKeyValPair().elementAt(loc.getOffset()).getValue();
    }

    @Override
    public void insert(String key, Value value) throws Exception
    {

    }

    public void insert(String key, Value value, Long startingNode)
    {
        FileNode<Value> rootNodeTemplate = getNode(startingNode);
        if (rootNodeTemplate.getSize() == 0)
        {
            insert(rootNodeTemplate, new Pair<>(key, value),
                    null, null);
            numberOfTermsAdded++;
            return;
        }
        FileDataLocation<Value> newLoc = findLoc(key, rootNodeTemplate);
        if (!thisDataExists(key, newLoc)) // if key not exists
        {
            insert(newLoc.getNode(), new Pair<>(key, value),
                    null, null);
            numberOfTermsAdded++;
        }
    }

    @Override
    public void update(String key, Value value)
    {

    }

    protected void invalidateRoots()
    {
        roots.clear();
    }

    protected Long addNewRoot(RamFileNode<Value> newRamRoot, RamFileNode<Value> ramParent,
                              int newRamPointerLocInParent)
    {
        FileNode<Value> newFileNode = convertRamNodeToFileNode(newRamRoot, null);
        roots.put(newFileNode.getMyPointer(),
                new RootInfo(newFileNode.getMyPointer(), new RamDataLocation<>(ramParent, newRamPointerLocInParent)));
        return newFileNode.getMyPointer();
    }

    @Override
    protected void createParentIfRequired(FileNode<Value> oldNodeTemplate, FileNode<Value> newNodeTemplate)
    {
        // create parent is not required any time
    }

    @Override
    protected void addVictimToParent(FileNode<Value> smallerChild, int victim, FileNode<Value> biggerChild)
    {
        if (smallerChild.getParent() == null)
        {
            // we should add the new node as a root to roots
            RootInfo oldNodeRootInfo = roots.get(smallerChild.getMyPointer());

            RamDataLocation<Value> tempRamDataLocation = new RamDataLocation<>(oldNodeRootInfo.locationDetailsInParent.getNode(),
                    oldNodeRootInfo.locationDetailsInParent.getOffset() + 1);

            RootInfo tempRootInfo = new RootInfo(biggerChild.getMyPointer(), tempRamDataLocation);

            roots.put(biggerChild.getMyPointer(), tempRootInfo);
            ramFileBtree.insert(roots.get(smallerChild.getMyPointer()).locationDetailsInParent.getNode(),
                    smallerChild.getKeyValPair().remove(victim),
                    null, null,
                    biggerChild.getMyPointer(), smallerChild.getMyPointer());
        } else
        {
            FileNode<Value> parentNodeTemplate = getNode(smallerChild.getParent());
            insert(parentNodeTemplate, smallerChild.getKeyValPair().remove(victim), biggerChild.getMyPointer(),
                    smallerChild.getMyPointer());
        }
    }

    private FileNode<Value> convertRamNodeToFileNode(RamFileNode<Value> newRamNode, Long parent)
    {
        FileNode<Value> newFileNode = createNewMiddleNode(parent);

        newFileNode.setKeyValPair(newRamNode.getKeyValPair());
        newFileNode.setId(newRamNode.getId());
        newRamNode.setKeyValPair(null);
        if (newRamNode.isChildAreOnFile())
        {
            newFileNode.setChild(newRamNode.getFileChild());
            for (int i = 0; i < newFileNode.getChild().size(); i++)
            {
                FileNode<Value> childNode = getNode(newFileNode.getChild().elementAt(i));
                childNode.setParent(newFileNode.getMyPointer());
                childNode.commitChanges();
            }
        } else
        {
            for (int i = 0; i < newRamNode.getChild().size(); i++)
            {
                RamFileNode<Value> tempChild = newRamNode.getChild().elementAt(i);
                if (tempChild == null)
                    newFileNode.getChild().add(null);
                else
                {
                    FileNode<Value> childNode = convertRamNodeToFileNode(tempChild, newFileNode.getMyPointer());
                    newRamNode.getChild().setElementAt(null, i);
                    newFileNode.getChild().add(childNode.getMyPointer());
                }

            }

        }
        newRamNode.setFileChild(null);
        newRamNode.setChild(null);
        newRamNode.setParent(null);
        newFileNode.commitChanges();
        return newFileNode;
    }

    public void updateParent(Long pointer, RamFileNode node)
    {
        roots.get(pointer).locationDetailsInParent.setNode(node);
    }

    public void update(String key, Value value, Long startingNode)
    {
        FileNode<Value> rootNodeTemplate = getNode(startingNode);
        if (rootNodeTemplate.getSize() == 0)
            return;
        FileDataLocation<Value> loc = findLoc(key, rootNodeTemplate);
        if (!thisDataExists(key, loc))
            return;
        updateValue(key, value, loc.getNode(), loc.getOffset());
    }

    public void invalidateCache()
    {
        nodeCache.clear();
    }

    public String toString()
    {
//        return "";
        Vector<FileNode<Value>> nodeTemplateQ = new Vector<>();
        Vector<RootInfo> tempRoots = new Vector<>(roots.values());
        for (int i = 0; i < tempRoots.size(); i++)
            nodeTemplateQ.add(getNode(tempRoots.elementAt(i).rootPointer));
        nodeTemplateQ.add(null);
        return toString(nodeTemplateQ, 1);
    }

    class RootInfo
    {
        protected Long rootPointer;
        protected RamDataLocation<Value> locationDetailsInParent;

        public RootInfo(Long rootPointer, RamDataLocation<Value> locationDetailsInParent)
        {
            this.rootPointer = rootPointer;
            this.locationDetailsInParent = locationDetailsInParent;
        }
    }
}
