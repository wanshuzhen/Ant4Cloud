package Aco;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Log;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.workflowsim.Task;
import org.workflowsim.utils.Parameters;
import org.workflowsim.utils.ReplicaCatalog;

public class WorkflowParser {
	 /**
     * The path to data size file.
     */
    private String fileSizePath;
    /**
     * The path to runtime file.
     */
    private String runtimePath;
    /**
     * The path to DAX file.
     */
    private String daxPath;
    /**
     * The path to DAX files.
     */
    private List<String> daxPaths;
    /**
     * All tasks.
     */
    private List<Task> taskList;
    /**
     * User id. used to create a new task.
     */
    private int userId;
    
    /**
     * current job id. In case multiple workflow submission
     */
    private int jobIdStartsFrom;

    /**
     * Gets the task list
     *
     * @return the task list
     */
    @SuppressWarnings("unchecked")
    public List<Task> getTaskList() {
        return (List<Task>) taskList;
    }

    /**
     * Sets the task list
     *
     * @param taskList the task list
     */
    protected void setTaskList(List<Task> taskList) {
        this.taskList = taskList;
    }
    /**
     * Map from task name to task.
     */
    protected Map<String, Task> mName2Task;

    /**
     * Initialize a WorkflowParser
     *
     * @param userId the user id. Currently we have just checked single user
     * mode
     */
    public WorkflowParser(int userId) {
        this.userId = userId;
        this.mName2Task = new HashMap<String, Task>();

        this.fileSizePath = Parameters.getDatasizePath();
        this.daxPath = Parameters.getDaxPath();
        this.daxPaths = Parameters.getDAXPaths();
        this.runtimePath = Parameters.getRuntimePath();
        this.jobIdStartsFrom = 0;
            
        setTaskList(new ArrayList<Task>());

    }

    public WorkflowParser(int userId, String fileSizePath, String runtimePath, String daxPath) {

        this(userId);
        this.fileSizePath = fileSizePath;
        this.runtimePath = runtimePath;
        this.daxPath = daxPath;
    }

    /**
     * Start to parse a workflow which includes text files and xml files.
     */
    public void parse() {
        if(this.daxPath != null){
            parseXmlFile(this.daxPath);
        } else if(this.daxPaths != null){
            for(String path: this.daxPaths){
                parseXmlFile(path);
            }
        }
    }

    /**
     * Sets the depth of a task
     *
     * @param task the task
     * @param depth the depth
     */
    private void setDepth(Task task, int depth) {
        if (depth > task.getDepth()) {
            task.setDepth(depth);
        }
        for (Iterator it = task.getChildList().iterator(); it.hasNext();) {
            Task cTask = (Task) it.next();
            setDepth(cTask, task.getDepth() + 1);
        }
    }

    /**
     * Parse a DAX file with jdom
     */
    private void parseXmlFile(String path) {

        try {

            SAXBuilder builder = new SAXBuilder();
            //parse using builder to get DOM representation of the XML file
            Document dom = builder.build(new File(path));
            Element root = dom.getRootElement();
            List list = root.getChildren();
            for (Iterator it = list.iterator(); it.hasNext();) {
                Element node = (Element) it.next();
                if (node.getName().toLowerCase().equals("job")) {

                    long length = 0;
                    String nodeName = node.getAttributeValue("id");
                    String nodeType = node.getAttributeValue("name");

                    /**
                     * capture runtime. If not exist, by default the runtime is
                     * 0
                     */
                    double runtime = 0.0;
                    if (node.getAttributeValue("runtime") != null) {
                        String nodeTime = node.getAttributeValue("runtime");
                        runtime = 1000 * Double.parseDouble(nodeTime);
                        length = (long) runtime;
                    } else {
                        Log.printLine("Cannot find runtime for " + nodeName + ",set it to be 0");
                    }
                    //multiple the scale, by default it is 1.0
                    length *= Parameters.getRuntimeScale();
                    
                    List fileList = node.getChildren();

                    List mFileList = new ArrayList<org.cloudbus.cloudsim.File>();

                    /**
                     * capture file.
                     */
                    for (Iterator itf = fileList.iterator(); itf.hasNext();) {
                        Element file = (Element) itf.next();
                        if (file.getName().toLowerCase().equals("uses")) {
                            String fileName = file.getAttributeValue("name");//DAX version 3.3
                            if (fileName == null) {
                                fileName = file.getAttributeValue("file");//DAX version 3.0
                            }
                            if (fileName == null) {
                                Log.print("Error in parsing xml");
                            }

                            String inout = file.getAttributeValue("link");
                            double size = 0.0;
                            
                            String fileSize = file.getAttributeValue("size");
                            if (fileSize != null) {
                                size = Double.parseDouble(fileSize) /*/ 1024*/;
                            } else {
                                Log.printLine("File Size not found for " + fileName);
                            }
                            
                            /**
                             * a bug of cloudsim, size 0 causes a problem. 1 is
                             * ok.
                             */
                            if (size == 0) {
                                size++;
                            }
                            /**
                             * Sets the file type 1 is input 2 is output
                             */
                            int type = 0;
                            if (inout.equals("input")) {
                                type = Parameters.FileType.INPUT.value;
                            } else if (inout.equals("output")) {
                                type = Parameters.FileType.OUTPUT.value;
                            } else {
                                Log.printLine("Parsing Error");
                            }
                            org.cloudbus.cloudsim.File tFile;
                            /*
                             * Already exists an input file (forget output file)
                             */
                            if (size < 0) {
                                /*
                                 * Assuming it is a parsing error
                                 */
                                size = 0 - size;
                                Log.printLine("Size is negative, I assume it is a parser error");
                            }
                            if (type == Parameters.FileType.OUTPUT.value) {
                                /**
                                 * It is good that CloudSim does tell whether a
                                 * size is zero
                                 */
                                tFile = new org.cloudbus.cloudsim.File(fileName, (int) size);
                            } else if (ReplicaCatalog.containsFile(fileName)) {
                                tFile = ReplicaCatalog.getFile(fileName);
                            } else {

                                tFile = new org.cloudbus.cloudsim.File(fileName, (int) size);
                                ReplicaCatalog.setFile(fileName, tFile);
                            }

                            tFile.setType(type);
                            mFileList.add(tFile);

                        }

                    }
                    Task task;
                    //In case of multiple workflow submission. Make sure the jobIdStartsFrom is consistent. 
                    synchronized (this){
                        task = new Task(this.jobIdStartsFrom, length);
                        this.jobIdStartsFrom ++ ;
                    }
                    task.setType(nodeType);

                    task.setUserId(userId);
                    mName2Task.put(nodeName, task);


                    for (Iterator itm = mFileList.iterator(); itm.hasNext();) {
                        org.cloudbus.cloudsim.File file = (org.cloudbus.cloudsim.File) itm.next();
                        task.addRequiredFile(file.getName());
                    }

                    task.setFileList(mFileList);
                    this.getTaskList().add(task);

                    /**
                     * Add dependencies info.
                     */
                } else if (node.getName().toLowerCase().equals("child")) {
                    List pList = node.getChildren();
                    String childName = node.getAttributeValue("ref");
                    if (mName2Task.containsKey(childName)) {

                        Task childTask = (Task) mName2Task.get(childName);

                        for (Iterator itc = pList.iterator(); itc.hasNext();) {
                            Element parent = (Element) itc.next();
                            String parentName = parent.getAttributeValue("ref");
                            if (mName2Task.containsKey(parentName)) {
                                Task parentTask = (Task) mName2Task.get(parentName);
                                parentTask.addChild(childTask);
                                childTask.addParent(parentTask);
                            }

                        }
                    }
                }

            }
            /**
             * If a task has no parent, then it is root task.
             */
            ArrayList roots = new ArrayList<Task>();
            for (Iterator it = mName2Task.values().iterator(); it.hasNext();) {
                Task task = (Task) it.next();
                task.setDepth(0);
                if (task.getParentList().isEmpty()) {
                    roots.add(task);
                }
            }

            /**
             * Add depth from top to bottom.
             */
            for (Iterator it = roots.iterator(); it.hasNext();) {
                Task task = (Task) it.next();
                setDepth(task, 1);
            }
            /**
             * Clean them so as to save memory. Parsing workflow may take much memory
             */
            this.mName2Task.clear();//?


        } catch (JDOMException jde) {
            Log.printLine("JDOM Exception;Please make sure your dax file is valid");

        } catch (IOException ioe) {
            Log.printLine("IO Exception;Please make sure dax.path is correctly set in your config file");

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Parsing Exception");

        }
    }

}
