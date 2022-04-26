package test.camunda;

import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class HolidayRequest {

    private static ProcessEngine processEngine;
    private static RepositoryService repositoryService;
    private static RuntimeService runtimeService;
    private static TaskService taskService;

    private static Scanner scanner = new Scanner(System.in);
    private static ROLE currentRole;
    private static boolean choseRole() {
        try {
            System.out.println("Are you an EMPLOYEE or a MANAGER? (or STOP)");
            currentRole = ROLE.valueOf(scanner.nextLine());
            return true;
        } catch (Exception e) { return false; }
    }
    private static void beAnEmployee(ProcessDefinition processDefinition) {
        System.out.println("/**\n * Acting as an EMPLOYEE\n **/");
        Map<String, Object> variables = new HashMap<String, Object>();
        {
            System.out.println("Who are you?");
            String employee = scanner.nextLine();

            System.out.println("How many holidays do you want to request?");
            Integer nrOfHolidays = Integer.valueOf(scanner.nextLine());

            System.out.println("Why do you need them?");
            String description = scanner.nextLine();

            variables.put("employee", employee);
            variables.put("nrOfHolidays", nrOfHolidays);
            variables.put("description", description);
        }

        //ProcessInstance inst = startProcessById(processDefinition.getId(), variables);
        ProcessInstance inst = startProcessByKey(processDefinition.getKey(), variables);// key = "holidayRequest"
        System.out.println("Process started, #" + inst.getId());
    }
    private static void beAManager() {
        System.out.println("\n\n/**\n * Acting as a team MANAGER\n **/");
        List<Task> tasks = showTasks();

        if (tasks.size() > 0) {
            System.out.println("Which task would you like to complete?");
            int taskIndex = Integer.valueOf(scanner.nextLine());

            Task task = tasks.get(taskIndex - 1);
            Map<String, Object> processVariables = taskService.getVariables(task.getId());

            System.out.println(processVariables.get("employee") + " wants " +
                    processVariables.get("nrOfHolidays") + " of holidays. Do you approve this? (y/n)");
            boolean approved = scanner.nextLine().toLowerCase().equals("y");

            Map<String, Object> variables = new HashMap<String, Object>();
            variables.put("approved", approved);

            completeTask(task.getId(), variables);
        }
    }

    public static void main(String[] args) {
        initializeEngine();

        repositoryService = processEngine.getRepositoryService();
        runtimeService = processEngine.getRuntimeService();
        taskService  = processEngine.getTaskService();

        Deployment deployment = deploy("holiday-request.bpmn20.bpmn");
        if (deployment != null) {
            System.out.println("Deployment definition: ");
            System.out.println(" - ID: " + deployment.getId());
            System.out.println(" - Name: " + deployment.getName());
        }

        ProcessDefinition processDefinition = findProcess(deployment.getId());
        if (processDefinition != null) {
            System.out.println("Found process definition: ");
            System.out.println(" - ID: " + processDefinition.getId());
            System.out.println(" - Key: " + processDefinition.getKey());
            System.out.println(" - Name: " +  processDefinition.getName());
        }

        while( choseRole() ) {
            switch (currentRole) {
                case MANAGER:
                    beAManager();
                    break;
                default:
                case EMPLOYEE:
                    beAnEmployee(processDefinition);
                    break;
            }
        }

        metrics();

        ProcessEngines.destroy();
    }

    private static void initializeEngine() {
        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:camunda;DB_CLOSE_DELAY=-1")
                .setJdbcUsername("sa")
                .setJdbcPassword("iptv1024")
                .setJdbcDriver("org.h2.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        processEngine = cfg.buildProcessEngine();
    }

    public static Deployment deploy(String address) {
        return repositoryService.createDeployment()
                .addClasspathResource(address)
                .deploy();
    }

    public static ProcessDefinition findProcess(String id) {
        return repositoryService.createProcessDefinitionQuery()
                .deploymentId(id)
                .singleResult();
    }

    public static ProcessInstance startProcessById(String id, Map<String, Object> variables) {
        return runtimeService.startProcessInstanceById(id, variables);
    }
    public static ProcessInstance startProcessByKey(String key, Map<String, Object> variables) {
        return runtimeService.startProcessInstanceByKey(key, variables);
    }

    private static void metrics() {
        System.out.println("\n\n/**\n * System Metrics\n **/\n");
        HistoryService historyService = processEngine.getHistoryService();
        List<ProcessInstance> instances = processEngine.getRuntimeService()
                .createProcessInstanceQuery()
                .list();

        for(ProcessInstance processInstance: instances) {
            System.out.println("Metrics for process instance " + processInstance.getId());
            List<HistoricActivityInstance> activities =
                    historyService.createHistoricActivityInstanceQuery()
                            .processInstanceId(processInstance.getId())
                            .orderByHistoricActivityInstanceEndTime().asc()
                            .list();
            for (HistoricActivityInstance activity : activities) {
                System.out.println(activity.getActivityId() + " took "
                        + activity.getDurationInMillis() + " milliseconds");
            }
            System.out.println("");
        }
    }

    public static List<Task> showTasks() {
        List<Task> tasks = taskService.createTaskQuery().taskCandidateGroup("managers").list();
        if (tasks.isEmpty()) {
            System.out.println("Nothing to do!");
        }
        else {
            System.out.println("You have " + tasks.size() + " tasks:");
            for (int i = 0; i < tasks.size(); i++) {
                System.out.println((i + 1) + ") " + tasks.get(i).getName());
            }
        }

        return tasks;
    }

    public static void completeTask(String id, Map<String, Object> variables) {
        taskService.complete(id, variables);
    }
}