package com.linkedin.clustermanager.tools;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;

import com.linkedin.clustermanager.ClusterDataAccessor.InstanceConfigProperty;
import com.linkedin.clustermanager.ClusterManagementService;
import com.linkedin.clustermanager.ClusterManagerException;
import com.linkedin.clustermanager.ZNRecord;
import com.linkedin.clustermanager.agent.zk.ZKClusterManagementTool;
import com.linkedin.clustermanager.agent.zk.ZkClient;
import com.linkedin.clustermanager.util.ZKClientPool;

public class ClusterSetup
{
  public static final String zkServerAddress = "zkSvr";

  // List info about the cluster / DB/ Nodes
  public static final String listClusters = "listClusters";
  public static final String listResourceGroups = "listResourceGroups";
  public static final String listNodes = "listNodes";

  // Add and rebalance
  public static final String addCluster = "addCluster";
  public static final String addNode = "addNode";
  public static final String addResourceGroup = "addResourceGroup";
  public static final String addStateModelDef = "addStateModelDef";
  public static final String rebalance = "rebalance";

  // Query info (TBD in V2)
  public static final String clusterInfo = "clusterInfo";
  public static final String nodeInfo = "nodeInfo";
  public static final String resourceGroupInfo = "resourceGroupInfo";
  public static final String resourceInfo = "resourceInfo";

  // TODO: refactor
  // setup for file-based cluster manager
  // public static final String configFile = "configFile";

  // enable / disable nodes
  public static final String enableNode = "enableNode";

  public static final String help = "help";

  static Logger _logger = Logger.getLogger(ClusterSetup.class);
  String _zkServerAddress;

  public ClusterSetup(String zkServerAddress)
  {
    _zkServerAddress = zkServerAddress;
  }

  public void addCluster(String clusterName, boolean overwritePrevious)
  {
    ClusterManagementService managementTool = getClusterManagementTool();
    managementTool.addCluster(clusterName, overwritePrevious);
    StateModelConfigGenerator generator = new StateModelConfigGenerator();
    addStateModelDef(clusterName, "MasterSlave",
        generator.generateConfigForMasterSlave());
  }

  public void addNodesToCluster(String clusterName, String[] nodeInfoArray)
  {
    for (String nodeInfo : nodeInfoArray)
    {
      // the storage node info must be hostname:port format.
      if (nodeInfo.length() > 0)
      {
        addNodeToCluster(clusterName, nodeInfo);
      }
    }
  }

  public void addNodeToCluster(String clusterName, String nodeAddress)
  {
    // nodeAddress must be in host:port format
    int lastPos = nodeAddress.lastIndexOf(":");
    if (lastPos <= 0)
    {
      String error = "Invalid storage node info format: " + nodeAddress;
      _logger.warn(error);
      throw new ClusterManagerException(error);
    }
    String host = nodeAddress.substring(0, lastPos);
    String portStr = nodeAddress.substring(lastPos + 1);
    int port = Integer.parseInt(portStr);
    addNodeToCluster(clusterName, host, port);
  }

  public void addNodeToCluster(String clusterName, String host, int port)
  {
    ClusterManagementService managementTool = getClusterManagementTool();

    ZNRecord nodeConfig = new ZNRecord();
    String nodeId = host + "_" + port;
    nodeConfig.setId(nodeId);
    nodeConfig.setSimpleField(InstanceConfigProperty.HOST.toString(), host);
    nodeConfig
        .setSimpleField(InstanceConfigProperty.PORT.toString(), "" + port);
    nodeConfig.setSimpleField(InstanceConfigProperty.ENABLED.toString(),
        true + "");

    managementTool.addNode(clusterName, nodeConfig);
  }

  public ClusterManagementService getClusterManagementTool()
  {
    ZkClient zkClient = ZKClientPool.getZkClient(_zkServerAddress);
    return new ZKClusterManagementTool(zkClient);
  }

  public void addStateModelDef(String clusterName, String stateModelDef,
      ZNRecord record)
  {
    ClusterManagementService managementTool = getClusterManagementTool();
    managementTool.addStateModelDef(clusterName, stateModelDef, record);
  }

  public void addResourceGroupToCluster(String clusterName,
      String resourceGroup, int numResources, String stateModelRef)
  {
    ClusterManagementService managementTool = getClusterManagementTool();
    managementTool.addResourceGroup(clusterName, resourceGroup, numResources,
        stateModelRef);
  }
  
  public void dropResourceGroupToCluster(String clusterName,
      String resourceGroup)
  {
    ClusterManagementService managementTool = getClusterManagementTool();
    managementTool.dropResourceGroup(clusterName, resourceGroup);
  }

  public void rebalanceStorageCluster(String clusterName,
      String resourceGroupName, int replica)
  {
    ClusterManagementService managementTool = getClusterManagementTool();
    List<String> nodeNames = managementTool.getNodeNamesInCluster(clusterName);

    ZNRecord dbIdealState = managementTool.getResourceGroupIdealState(
        clusterName, resourceGroupName);
    int partitions = Integer
        .parseInt(dbIdealState.getSimpleField("partitions"));

    ZNRecord idealState = IdealStateCalculatorForStorageNode
        .calculateIdealState(nodeNames, partitions, replica, resourceGroupName);
    idealState.merge(dbIdealState);
    managementTool.setResourceGroupIdealState(clusterName, resourceGroupName,
        idealState);
  }

  /**
   * Sets up a cluster with 6 Nodes[localhost:8900 to localhost:8905], 1
   * resourceGroup[EspressoDB] with a replication factor of 3
   * 
   * @param clusterName
   */
  public void setupTestCluster(String clusterName)
  {
    addCluster(clusterName, true);
    String storageNodeInfoArray[] = new String[6];
    for (int i = 0; i < storageNodeInfoArray.length; i++)
    {
      storageNodeInfoArray[i] = "localhost:" + (8900 + i);
    }
    addNodesToCluster(clusterName, storageNodeInfoArray);
    addResourceGroupToCluster(clusterName, "TestDB", 10, "MasterSlave");
    rebalanceStorageCluster(clusterName, "TestDB", 3);
  }

  public static void printUsage(Options cliOptions)
  {
    HelpFormatter helpFormatter = new HelpFormatter();
    helpFormatter.printHelp("java " + ClusterSetup.class.getName(), cliOptions);
  }

  @SuppressWarnings("static-access")
  private static Options constructCommandLineOptions()
  {
    Option helpOption = OptionBuilder.withLongOpt(help)
        .withDescription("Prints command-line options info").create();

    Option zkServerOption = OptionBuilder.withLongOpt(zkServerAddress)
        .withDescription("Provide zookeeper address").create();
    zkServerOption.setArgs(1);
    zkServerOption.setRequired(true);
    zkServerOption.setArgName("ZookeeperServerAddress(Required)");

    Option listClustersOption = OptionBuilder.withLongOpt(listClusters)
        .withDescription("List existing clusters").create();
    listClustersOption.setArgs(0);
    listClustersOption.setRequired(false);

    Option listResourceGroupOption = OptionBuilder
        .withLongOpt(listResourceGroups)
        .withDescription("List resourceGroups hosted in a cluster").create();
    listResourceGroupOption.setArgs(1);
    listResourceGroupOption.setRequired(false);
    listResourceGroupOption.setArgName("clusterName");

    Option listNodesOption = OptionBuilder.withLongOpt(listNodes)
        .withDescription("List nodes in a cluster").create();
    listNodesOption.setArgs(1);
    listNodesOption.setRequired(false);
    listNodesOption.setArgName("clusterName");

    Option addClusterOption = OptionBuilder.withLongOpt(addCluster)
        .withDescription("Add a new cluster").create();
    addClusterOption.setArgs(1);
    addClusterOption.setRequired(false);
    addClusterOption.setArgName("clusterName");

    Option addNodeOption = OptionBuilder.withLongOpt(addNode)
        .withDescription("Add a new node to a cluster").create();
    addNodeOption.setArgs(2);
    addNodeOption.setRequired(false);
    addNodeOption.setArgName("clusterName nodeAddress(host:port)");

    Option addResourceGroupOption = OptionBuilder.withLongOpt(addResourceGroup)
        .withDescription("Add a resourceGroup to a cluster").create();
    addResourceGroupOption.setArgs(4);
    addResourceGroupOption.setRequired(false);
    addResourceGroupOption
        .setArgName("clusterName resourceGroupName partitionNo stateModelRef");

    Option addStateModelDefGroupOption = OptionBuilder
        .withLongOpt(addStateModelDef)
        .withDescription("Add a resourceGroup to a cluster").create();
    addStateModelDefGroupOption.setArgs(3);
    addStateModelDefGroupOption.setRequired(false);
    addStateModelDefGroupOption.setArgName("clusterName stateModelDef <text>");

    Option rebalanceOption = OptionBuilder.withLongOpt(rebalance)
        .withDescription("Rebalance a resourceGroup in a cluster").create();
    rebalanceOption.setArgs(3);
    rebalanceOption.setRequired(false);
    rebalanceOption.setArgName("clusterName resourceGroupName replicationNo");

    Option nodeInfoOption = OptionBuilder.withLongOpt(nodeInfo)
        .withDescription("Query info of a node in a cluster").create();
    nodeInfoOption.setArgs(2);
    nodeInfoOption.setRequired(false);
    nodeInfoOption.setArgName("clusterName nodeName");

    Option clusterInfoOption = OptionBuilder.withLongOpt(clusterInfo)
        .withDescription("Query info of a cluster").create();
    clusterInfoOption.setArgs(1);
    clusterInfoOption.setRequired(false);
    clusterInfoOption.setArgName("clusterName");

    Option resourceGroupInfoOption = OptionBuilder
        .withLongOpt(resourceGroupInfo)
        .withDescription("Query info of a resourceGroup").create();
    resourceGroupInfoOption.setArgs(2);
    resourceGroupInfoOption.setRequired(false);
    resourceGroupInfoOption.setArgName("clusterName resourceGroupName");

    Option partitionInfoOption = OptionBuilder.withLongOpt(resourceInfo)
        .withDescription("Query info of a partition").create();
    partitionInfoOption.setArgs(2);
    partitionInfoOption.setRequired(false);
    partitionInfoOption.setArgName("clusterName partitionName");

    Option enableNodeOption = OptionBuilder.withLongOpt(enableNode)
        .withDescription("Enable / disable a node").create();
    enableNodeOption.setArgs(3);
    enableNodeOption.setRequired(false);
    enableNodeOption.setArgName("clusterName nodeName true/false");

    // add an option group including either --zkSvr or --configFile
    /**
    Option fileOption = OptionBuilder.withLongOpt(configFile)
        .withDescription("Provide file to write states/messages").create();
    fileOption.setArgs(1);
    fileOption.setRequired(true);
    fileOption.setArgName("File to write states/messages (Optional)");
     **/
    OptionGroup optionGroup = new OptionGroup();
    optionGroup.addOption(zkServerOption);
    // optionGroup.addOption(fileOption);

    Options options = new Options();
    options.addOption(helpOption);
    // options.addOption(zkServerOption);
    options.addOption(rebalanceOption);
    options.addOption(addResourceGroupOption);
    options.addOption(addClusterOption);
    options.addOption(addNodeOption);
    options.addOption(listNodesOption);
    options.addOption(listResourceGroupOption);
    options.addOption(listClustersOption);
    options.addOption(rebalanceOption);
    options.addOption(nodeInfoOption);
    options.addOption(clusterInfoOption);
    options.addOption(resourceGroupInfoOption);
    options.addOption(partitionInfoOption);
    options.addOption(enableNodeOption);

    options.addOptionGroup(optionGroup);

    return options;
  }

  public static int processCommandLineArgs(String[] cliArgs) throws Exception
  {
    CommandLineParser cliParser = new GnuParser();
    Options cliOptions = constructCommandLineOptions();
    CommandLine cmd = null;

    try
    {
      cmd = cliParser.parse(cliOptions, cliArgs);
    } catch (ParseException pe)
    {
      System.err
          .println("CommandLineClient: failed to parse command-line options: "
              + pe.toString());
      printUsage(cliOptions);
      System.exit(1);
    }

    /**
    if (cmd.hasOption(configFile))
    {
      String file = cmd.getOptionValue(configFile);

      // for temporary test only, will move to command line
      // create fake db names
      List<FileBasedClusterManager.DBParam> dbParams = new ArrayList<FileBasedClusterManager.DBParam>();
      dbParams.add(new FileBasedClusterManager.DBParam("BizFollow", 1));
      dbParams.add(new FileBasedClusterManager.DBParam("BizProfile", 1));
      dbParams.add(new FileBasedClusterManager.DBParam("EspressoDB", 10));
      dbParams.add(new FileBasedClusterManager.DBParam("MailboxDB", 128));
      dbParams.add(new FileBasedClusterManager.DBParam("MyDB", 8));
      dbParams.add(new FileBasedClusterManager.DBParam("schemata", 1));
      String[] nodesInfo =
      { "localhost:8900" };

      // ClusterViewSerializer serializer = new ClusterViewSerializer(file);
      int replica = 0;
      ClusterView view = FileBasedClusterManager
          .generateStaticConfigClusterView(nodesInfo, dbParams, replica);

      // byte[] bytes;
      ClusterViewSerializer.serialize(view, new File(file));
      // System.out.println(new String(bytes));

      ClusterView restoredView = ClusterViewSerializer.deserialize(new File(
          file));
      // System.out.println(restoredView);

      byte[] bytes = ClusterViewSerializer.serialize(restoredView);
      // System.out.println(new String(bytes));

      return 0;
    }
    **/
    
    ClusterSetup setupTool = new ClusterSetup(
        cmd.getOptionValue(zkServerAddress));

    if (cmd.hasOption(addCluster))
    {
      String clusterName = cmd.getOptionValue(addCluster);
      setupTool.addCluster(clusterName, false);
      return 0;
    }

    if (cmd.hasOption(addNode))
    {
      String clusterName = cmd.getOptionValues(addNode)[0];
      String nodeAddressInfo = cmd.getOptionValues(addNode)[1];
      String[] nodeAddresses = nodeAddressInfo.split(";");
      setupTool.addNodesToCluster(clusterName, nodeAddresses);
      return 0;
    }

    if (cmd.hasOption(addResourceGroup))
    {
      String clusterName = cmd.getOptionValues(addResourceGroup)[0];
      String resourceGroupName = cmd.getOptionValues(addResourceGroup)[1];
      int partitions = Integer
          .parseInt(cmd.getOptionValues(addResourceGroup)[2]);
      String stateModelRef = cmd.getOptionValues(addResourceGroup)[3];
      setupTool.addResourceGroupToCluster(clusterName, resourceGroupName,
          partitions, stateModelRef);
      return 0;
    }

    if (cmd.hasOption(rebalance))
    {
      String clusterName = cmd.getOptionValues(rebalance)[0];
      String resourceGroupName = cmd.getOptionValues(rebalance)[1];
      int replicas = Integer.parseInt(cmd.getOptionValues(rebalance)[2]);
      setupTool.rebalanceStorageCluster(clusterName, resourceGroupName,
          replicas);
      return 0;
    }

    if (cmd.hasOption(listClusters))
    {
      List<String> clusters = setupTool.getClusterManagementTool()
          .getClusters();

      System.out.println("Existing clusters:");
      for (String cluster : clusters)
      {
        System.out.println(cluster);
      }
      return 0;
    }

    if (cmd.hasOption(listResourceGroups))
    {
      String clusterName = cmd.getOptionValue(listResourceGroups);
      List<String> resourceGroupNames = setupTool.getClusterManagementTool()
          .getResourceGroupsInCluster(clusterName);

      System.out.println("Existing databses in cluster " + clusterName + ":");
      for (String resourceGroupName : resourceGroupNames)
      {
        System.out.println(resourceGroupName);
      }
    } else if (cmd.hasOption(listNodes))
    {
      String clusterName = cmd.getOptionValue(listNodes);
      List<String> nodes = setupTool.getClusterManagementTool()
          .getNodeNamesInCluster(clusterName);

      System.out.println("Nodes in cluster " + clusterName + ":");
      for (String nodeName : nodes)
      {
        System.out.println(nodeName);
      }
    }
    // TODO: add implementation in CM V2
    else if (cmd.hasOption(nodeInfo))
    {
      // print out current states and
    } else if (cmd.hasOption(resourceGroupInfo))
    {
      // print out partition number, db name and replication number
      // Also the ideal states and current states
    } else if (cmd.hasOption(resourceInfo))
    {
      // print out where the partition master / slaves locates
    } else if (cmd.hasOption(enableNode))
    {
      String clusterName = cmd.getOptionValues(enableNode)[0];
      String instanceName = cmd.getOptionValues(enableNode)[1];
      boolean enabled = Boolean.parseBoolean(cmd.getOptionValues(enableNode)[1]
          .toLowerCase());

      setupTool.getClusterManagementTool().enableInstance(clusterName,
          instanceName, enabled);
    } else if (cmd.hasOption(help))
    {
      printUsage(cliOptions);
      return 0;
    }
    return 0;
  }

  /**
   * @param args
   * @throws Exception
   * @throws JsonMappingException
   * @throws JsonGenerationException
   */
  public static void main(String[] args) throws Exception
  {
    // For temporary test only, remove later
    Logger.getRootLogger().setLevel(Level.ERROR);
    if (args.length == 0)
    {
      new ClusterSetup("localhost:2181")
          .setupTestCluster("storage-integration-cluster");
      new ClusterSetup("localhost:2181")
          .setupTestCluster("relay-integration-cluster");
      System.exit(0);
    }

    int ret = processCommandLineArgs(args);
    System.exit(ret);
  }
}
