package DataStructures.Tree;

import DataStructures.Tree.Nodes.DataLocations.RamDataLocation;
import DataStructures.Tree.Nodes.FileNode;
import DataStructures.Tree.Nodes.RamFileNode;
import FileManagement.RandomAccessFileManager;
import Primitives.Interfaces.Parsable;
import Primitives.Interfaces.Sizeofable;
import javafx.util.Pair;

import java.io.IOException;
import java.util.Vector;

public class RamFileBtree<Value extends Sizeofable & Parsable>
{
    private static final long MEMORY_LIMIT = 30;
    private static int idCounter = 0;
    protected final int KEY_MAX_SIZE;
    protected final int VALUE_MAX_SIZE;
    protected final int HALF_MAX_SIZE, MAX_SIZE;
    protected final long MB = 1024 * 1024;
    protected final Class valueClassType;
    protected final int fileID;
    protected int depth;
    protected RamFileNode<Value> root;
    private ExtendedFileBtree<Value> extendedFileBtree;
    private boolean depthIncremented;
    private long lastReadLocation;
    private boolean isFirstRead;
    private long numberOfTermsAdded;

    public RamFileBtree(int keyMaxSize, int valueMaxSize, int halfMaxSize, Class valueClassType, int fileID)
    {
        KEY_MAX_SIZE = keyMaxSize;
        VALUE_MAX_SIZE = valueMaxSize;
        this.HALF_MAX_SIZE = halfMaxSize;
        this.valueClassType = valueClassType;
        this.fileID = fileID;
        this.MAX_SIZE = 2 * halfMaxSize - 1;
        depth = 1;
        lastReadLocation = 0;
        isFirstRead = true;
        depthIncremented = false;

        root = createNewLeafNode(null);
        extendedFileBtree = new ExtendedFileBtree<>(keyMaxSize, valueMaxSize, halfMaxSize, valueClassType, this, fileID);
    }

    public static int getNewID()
    {
        return ++idCounter;
    }

    public Value search(String key)
    {
        RamFileNode<Value> rootNodeTemplate = getRootNode();
        if (rootNodeTemplate.getSize() == 0)
            return null;
        RamDataLocation<Value> loc = findLoc(key, rootNodeTemplate);
        if (!thisDataExists(key, loc))
            return searchOnExtendedFileBtree(key, loc);
        return loc.getNode().getKeyValPair().elementAt(loc.getOffset()).getValue();
//        return search(key, rootNodeTemplate);
    }

    public void update(String key, Value value)
    {
        RamFileNode<Value> rootNodeTemplate = getRootNode();
        if (rootNodeTemplate.getSize() == 0)
            return;
        RamDataLocation<Value> loc = findLoc(key, rootNodeTemplate);
        if (!thisDataExists(key, loc))
        {
            updateOnExtendedFileBtree(key, value, loc);
            return;
        }
        updateValue(key, value, loc.getNode(), loc.getOffset());
    }

    public void insert(String key, Value value) throws Exception
    {
        if (key.getBytes().length > KEY_MAX_SIZE || value.sizeof() > VALUE_MAX_SIZE)
            throw new Exception("length exceeded");
        RamFileNode<Value> rootNodeTemplate = getRootNode();
        if (rootNodeTemplate.getSize() == 0)
        {
            insert(rootNodeTemplate, new Pair<>(key, value),
                    null, null,
                    null, null);
            numberOfTermsAdded++;
            checkMemoryAndFreeIfRequired();
            return;
        }
        RamDataLocation<Value> newLoc = findLoc(key, rootNodeTemplate);
        if (!thisDataExists(key, newLoc)) // if key not exists
        {
            if (newLoc.getNode().isChildAreOnFile())
                extendedFileBtree.insert(key, value, newLoc.getNode().getFileChild().elementAt(newLoc.getOffset()));
            else
            {
                insert(newLoc.getNode(), new Pair<>(key, value),
                        null, null,
                        null, null);
                numberOfTermsAdded++;
            }
            checkMemoryAndFreeIfRequired();
        }
    }

    public void initializeForSequentialRead()
    {
        isFirstRead = true;
        lastReadLocation = 0;
        storeNodesOnFile();
    }

    public Vector<Pair<String, Value>> getNextNode()
    {
        if (isFirstRead)
            return getRootNode().getKeyValPair();

        FileNode<Value> node = null;
        try
        {
            if (lastReadLocation >= RandomAccessFileManager.getInstance(fileID).getChannel().size())
                return null;
            node = extendedFileBtree.getNode(lastReadLocation);
            lastReadLocation = RandomAccessFileManager.getInstance(fileID).getFilePointer();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return node.getKeyValPair();
    }

    private void updateOnExtendedFileBtree(String key, Value value, RamDataLocation<Value> loc)
    {
        if (loc.getNode().isChildAreOnFile())
            extendedFileBtree.update(key, value, loc.getNode().getFileChild().elementAt(loc.getOffset()));
    }

    protected void checkMemoryAndFreeIfRequired()
    {
//        if(depthIncremented)
//        {
//            storeNodesOnFile();
//            depthIncremented = false;
//            return;
//        }
        if (memoryLimitExceeded())
        {
            if (!getRootNode().isChildAreOnFile())
                storeNodesOnFile();
            extendedFileBtree.invalidateCache();
            Runtime.getRuntime().gc();
        }

    }

    private void storeNodesOnFile()
    {
        System.out.println("depth: " + depth);
        extendedFileBtree.invalidateRoots();
        RamFileNode<Value> rootNode = getRootNode();
        rootNode.setChildAreOnFile(true);
        for (int i = 0; i <= rootNode.getSize(); i++)
        {
            Long childFileNodePtr = extendedFileBtree.addNewRoot(rootNode.getChild().elementAt(i), rootNode, i);
            rootNode.getFileChild().set(i, childFileNodePtr);
            rootNode.getChild().set(i, null);
        }
    }

    protected boolean memoryLimitExceeded()
    {
        double freeMomory = Runtime.getRuntime().freeMemory() / MB;
        if (freeMomory < MEMORY_LIMIT)
        {
//            System.out.println("Memory freeing -- >> freeMemory: " + freeMomory);
            return true;
        }
        return false;
    }

    private Value searchOnExtendedFileBtree(String key, RamDataLocation<Value> loc)
    {
        if (loc.getNode().isChildAreOnFile())
            return extendedFileBtree.search(key, loc.getNode().getFileChild().elementAt(loc.getOffset()));
        return null;
    }

    protected RamFileNode<Value> getRootNode()
    {
        return root;
    }

    private RamFileNode<Value> getNode(RamFileNode<Value> node)
    {
        return node;
    }

    protected void createParentIfRequired(RamFileNode<Value> oldNodeTemplate, RamFileNode<Value> newNodeTemplate)
    {
        if (oldNodeTemplate.getParent() == null)
        {
            RamFileNode<Value> parentNodeTemplate = createNewMiddleNode(null);
            oldNodeTemplate.setParent(parentNodeTemplate.getMyPointer());
            root = parentNodeTemplate.getMyPointer();
//            newNode.parent = root;
            depth++;
            depthIncremented = true;
        }
    }

    protected void addVictimToParent(RamFileNode<Value> startingNode, int victim, RamFileNode<Value> newNodeTemplate)
    {
        RamFileNode<Value> parentNodeTemplate = getNode(startingNode.getParent());
        insert(parentNodeTemplate, startingNode.getKeyValPair().remove(victim),
                newNodeTemplate.getMyPointer(), startingNode.getMyPointer(),
                null, null);
    }

    protected void insert(RamFileNode<Value> startingNode, Pair<String, Value> newData,
                          RamFileNode<Value> biggerChild, RamFileNode<Value> smallerChild,
                          Long biggerFileChild, Long smallerFileChild)
    {
//        System.out.println("adding data, pointer: " + startingNode.myPointer + " getNode() size: " + startingNode.getSize());
        if (startingNode.getSize() == 0)
        {
            startingNode.getKeyValPair().add(newData);
            startingNode.getChild().add(smallerChild);
            startingNode.getFileChild().add(smallerFileChild);
            if (smallerChild != null)
            {
                RamFileNode<Value> smallerChildNodeTemplate = getNode(smallerChild);
                smallerChildNodeTemplate.setParent(startingNode.getMyPointer());
            }
            if (smallerFileChild != null)
                extendedFileBtree.updateParent(smallerFileChild, startingNode.getMyPointer());

            startingNode.getChild().add(biggerChild);
            startingNode.getFileChild().add(biggerFileChild);
            if (biggerChild != null)
            {
                RamFileNode<Value> biggerChildNodeTemplate = getNode(biggerChild);
                biggerChildNodeTemplate.setParent(startingNode.getMyPointer());
            }
            if (biggerFileChild != null)
                extendedFileBtree.updateParent(biggerFileChild, startingNode.getMyPointer());
        } else
        {
            int location = startingNode.binarySearchForLocationToAdd(newData.getKey());
            if (location == startingNode.getKeyValPair().size())
            {
                startingNode.getKeyValPair().add(newData);
                startingNode.getChild().add(biggerChild);
                startingNode.getFileChild().add(biggerFileChild);
            } else
            {
                startingNode.getKeyValPair().insertElementAt(newData, location);
                startingNode.getChild().insertElementAt(biggerChild, location + 1);
                startingNode.getFileChild().insertElementAt(biggerFileChild, location + 1);
            }
            if (biggerChild != null)
            {
                RamFileNode<Value> biggerChildNodeTemplate = getNode(biggerChild);
                biggerChildNodeTemplate.setParent(startingNode.getMyPointer());
            }
            if (biggerFileChild != null)
                extendedFileBtree.updateParent(biggerFileChild, startingNode.getMyPointer());
            if (smallerChild != null)
            {
                startingNode.getChild().set(location, smallerChild);
                RamFileNode<Value> smallerChildNodeTemplate = getNode(smallerChild);
                smallerChildNodeTemplate.setParent(startingNode.getMyPointer());
            }
            if (smallerFileChild != null)
            {
                startingNode.getFileChild().set(location, smallerFileChild);
                extendedFileBtree.updateParent(smallerFileChild, startingNode.getMyPointer());
            }
            if (startingNode.getSize() > MAX_SIZE)
                splitCurrentNode(startingNode);
        }
//        System.out.println("data added, pointer: " + startingNode.myPointer + " getNode() size: " + startingNode.getSize());
    }

    protected Value returnValue(String key, RamFileNode<Value> startingNodeTemplate, int i1)
    {
        return startingNodeTemplate.getKeyValPair().elementAt(i1).getValue();
    }

    protected void updateValue(String key, Value value, RamFileNode<Value> startingNodeTemplate, int i1)
    {
        Pair<String, Value> oldKeyValuePair = startingNodeTemplate.getKeyValPair().elementAt(i1);
        startingNodeTemplate.getKeyValPair().set(i1, new Pair<>(oldKeyValuePair.getKey(), value));
    }

    protected RamFileNode<Value> createNewMiddleNode(RamFileNode<Value> parent)
    {
        RamFileNode<Value> resultNode = new RamFileNode<Value>(HALF_MAX_SIZE, parent);
        return resultNode;
    }

    protected RamFileNode<Value> createNewLeafNode(RamFileNode<Value> parent)
    {
        return createNewMiddleNode(parent);
    }

    protected boolean thisDataExists(String key, RamDataLocation<Value> newLoc)
    {
        if (newLoc.getOffset() == newLoc.getNode().getSize() || key.compareTo(((Pair<String, Value>) newLoc.getNode().getKeyValPair().elementAt(newLoc.getOffset())).getKey()) != 0)
            return false;
        return true;
    }

    protected RamFileNode<Value>[] splitCurrentNode(RamFileNode<Value> startingNode)
    {
        RamFileNode<Value> newNodeTemplate = createNewLeafNode(startingNode.getParent());
        int victim = HALF_MAX_SIZE;
        int offset = victim + 1;
        newNodeTemplate.setChildAreOnFile(startingNode.isChildAreOnFile());
        moveDataToSiblingAndCreateParentIfRequired(startingNode, newNodeTemplate, offset);
        addVictimToParent(startingNode, victim, newNodeTemplate);
        return null;
    }

    protected void moveDataToSiblingAndCreateParentIfRequired(RamFileNode<Value> oldNodeTemplate, RamFileNode<Value> newNodeTemplate, int offset)
    {
        for (int i = offset; i <= MAX_SIZE; i++)
        {
            if (i == offset) // first getNode()
            {
                RamFileNode<Value> smallerChild = oldNodeTemplate.getChild().remove(offset), biggerChild = oldNodeTemplate.getChild().remove(offset);
                Long smallerFileChild = oldNodeTemplate.getFileChild().remove(offset), biggerFileChild = oldNodeTemplate.getFileChild().remove(offset);
                insert(newNodeTemplate, oldNodeTemplate.getKeyValPair().remove(offset),
                        biggerChild, smallerChild,
                        biggerFileChild, smallerFileChild);
            } else
                insert(newNodeTemplate, oldNodeTemplate.getKeyValPair().remove(offset),
                        oldNodeTemplate.getChild().remove(offset), null,
                        oldNodeTemplate.getFileChild().remove(offset), null);
        }
        createParentIfRequired(oldNodeTemplate, newNodeTemplate);
    }

    protected RamDataLocation<Value> findLoc(String key, RamFileNode<Value> startingNodeTemplate)
    {
        if (startingNodeTemplate.getSize() == 0) return new RamDataLocation<Value>(startingNodeTemplate, 0);
        RamFileNode<Value> nextChild;
        int i1 = startingNodeTemplate.binarySearchForLocationToAdd(key);
        if (i1 == startingNodeTemplate.getSize())
            nextChild = startingNodeTemplate.getChild().elementAt(i1);
        else if (startingNodeTemplate.getKeyValPair().elementAt(i1).getKey().compareTo(key) == 0)
            return new RamDataLocation<Value>(startingNodeTemplate, i1);
        else
            nextChild = startingNodeTemplate.getChild().elementAt(i1);
        return (nextChild == null ? new RamDataLocation<Value>(startingNodeTemplate, i1) : findLoc(key, getNode(nextChild)));
    }

    protected String toString(Vector<RamFileNode<Value>> nodeTemplateQ, int stringDepth)
    {
        if (nodeTemplateQ.size() == 0)
            return "";
        RamFileNode<Value> currentNodeTemplate = nodeTemplateQ.remove(0);
        if (currentNodeTemplate == null)
        {
            if (stringDepth < this.depth)
            {
                stringDepth++;
                nodeTemplateQ.add(null);
            }
            return "\n" + toString(nodeTemplateQ, stringDepth);
        }
        for (int i = 0; i < currentNodeTemplate.getChild().size(); i++)
        {
            RamFileNode<Value> childNode = currentNodeTemplate.getChild().elementAt(i);
            if (childNode != null) nodeTemplateQ.add(getNode(childNode));
        }
        return currentNodeTemplate.toString() + "\t" + toString(nodeTemplateQ, stringDepth);
    }

    public String toString()
    {
        Vector<RamFileNode<Value>> nodeTemplateQ = new Vector<>();
        nodeTemplateQ.add(getRootNode());
        nodeTemplateQ.add(null);
        return toString(nodeTemplateQ, 1) + "\n\n***********************\n\n" + extendedFileBtree.toString();
    }

    public long getNumberOfTermsAdded()
    {
        return numberOfTermsAdded + extendedFileBtree.getNumberOfTermsAdded();
    }
}