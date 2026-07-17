package com.pessoal.agenda.app;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.repository.*;
import com.pessoal.agenda.service.AlertService;
import com.pessoal.agenda.service.CategoryService;
import com.pessoal.agenda.service.DashboardService;
import com.pessoal.agenda.service.InsightsService;
import com.pessoal.agenda.service.NaturalLanguageTaskParser;
import com.pessoal.agenda.service.StudyAttendanceService;
import com.pessoal.agenda.service.TaskPrioritizer;
import com.pessoal.agenda.service.TaskService;

/**
 * Composition Root (DI manual): centraliza a montagem de dependencias da aplicacao.
 *
 * Beneficios:
 * - Evita "new" espalhado pela UI
 * - Facilita testes por injeção de dependencias
 * - Ajuda manutencao e documentacao arquitetural
 */
public class AppContext {

    private final Database database;

    private final TaskRepository taskRepository;
    private final ChecklistRepository checklistRepository;
    private final FinanceRepository financeRepository;
    private final SalesRepository salesRepository;
    private final InventoryRepository inventoryRepository;
    private final StudyRepository studyRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final StudyEntryRepository studyEntryRepository;
    private final ProjectIdeaRepository projectIdeaRepository;
    private final IdeaChecklistRepository ideaChecklistRepository;
    private final TaskChecklistRepository taskChecklistRepository;
    private final CategoryRepository categoryRepository;
    private final ProtocolRepository protocolRepository;
    private final StudyScheduleRepository studyScheduleRepository;
    private final StudyCompensationRepository studyCompensationRepository;
    private final com.pessoal.agenda.repository.StudyStatusLogRepository studyStatusLogRepository;
    private final com.pessoal.agenda.repository.TaskSessionRepository taskSessionRepository;
    private final com.pessoal.agenda.repository.GoogleTasksMappingRepository googleTasksMappingRepository;

    private final TaskService taskService;
    private final AlertService alertService;
    private final DashboardService dashboardService;
    private final CategoryService categoryService;
    private final StudyAttendanceService studyAttendanceService;
    private final InsightsService insightsService;
    private final NaturalLanguageTaskParser naturalLanguageTaskParser;
    private final TaskPrioritizer taskPrioritizer;

    private AppContext() {
        this.database = new Database();
        this.database.runMigrations();

        this.taskRepository = new TaskRepository(database);
        this.checklistRepository = new ChecklistRepository(database);
        this.financeRepository = new FinanceRepository(database);
        this.salesRepository = new SalesRepository(database);
        this.inventoryRepository = new InventoryRepository(database);
        this.studyRepository = new StudyRepository(database);
        this.studyPlanRepository = new StudyPlanRepository(database);
        this.studyEntryRepository = new StudyEntryRepository(database);
        this.projectIdeaRepository = new ProjectIdeaRepository(database);
        this.ideaChecklistRepository = new IdeaChecklistRepository(database);
        this.taskChecklistRepository = new TaskChecklistRepository(database);
        this.categoryRepository = new CategoryRepository(database);
        this.protocolRepository = new ProtocolRepository(database);
        this.studyScheduleRepository = new StudyScheduleRepository(database);
        this.studyCompensationRepository = new StudyCompensationRepository(database);
        this.studyStatusLogRepository = new com.pessoal.agenda.repository.StudyStatusLogRepository(database);
        this.taskSessionRepository = new com.pessoal.agenda.repository.TaskSessionRepository(database);
        this.googleTasksMappingRepository = new com.pessoal.agenda.repository.GoogleTasksMappingRepository(database);

        this.taskService = new TaskService(taskRepository);
        this.alertService = new AlertService(database, taskRepository, financeRepository);
        this.dashboardService = new DashboardService(taskRepository, checklistRepository, financeRepository, inventoryRepository, studyRepository, projectIdeaRepository);
        this.categoryService = new CategoryService(categoryRepository);
        this.studyAttendanceService = new StudyAttendanceService(
                studyScheduleRepository, studyEntryRepository, studyCompensationRepository,
                studyStatusLogRepository);
        this.insightsService = new InsightsService(taskRepository);
        this.naturalLanguageTaskParser = new NaturalLanguageTaskParser();
        this.taskPrioritizer = new TaskPrioritizer();
        this.categoryService.seedDefaults();
    }

    public static AppContext create() {
        return new AppContext();
    }

    public Database database() {
        return database;
    }

    public TaskRepository taskRepository() {
        return taskRepository;
    }

    public ChecklistRepository checklistRepository() {
        return checklistRepository;
    }

    public FinanceRepository financeRepository() {
        return financeRepository;
    }

    public SalesRepository salesRepository() {
        return salesRepository;
    }

    public InventoryRepository inventoryRepository() {
        return inventoryRepository;
    }

    public StudyRepository studyRepository() {
        return studyRepository;
    }

    public StudyPlanRepository studyPlanRepository() {
        return studyPlanRepository;
    }

    public StudyEntryRepository studyEntryRepository() {
        return studyEntryRepository;
    }

    public ProjectIdeaRepository projectIdeaRepository() {
        return projectIdeaRepository;
    }

    public IdeaChecklistRepository ideaChecklistRepository() {
        return ideaChecklistRepository;
    }

    public TaskChecklistRepository taskChecklistRepository() {
        return taskChecklistRepository;
    }

    public CategoryRepository categoryRepository() {
        return categoryRepository;
    }

    public ProtocolRepository protocolRepository() {
        return protocolRepository;
    }

    public TaskService taskService() {
        return taskService;
    }

    public AlertService alertService() {
        return alertService;
    }

    public DashboardService dashboardService() {
        return dashboardService;
    }

    public CategoryService categoryService() {
        return categoryService;
    }

    public StudyScheduleRepository studyScheduleRepository() {
        return studyScheduleRepository;
    }

    public StudyCompensationRepository studyCompensationRepository() {
        return studyCompensationRepository;
    }

    public com.pessoal.agenda.repository.StudyStatusLogRepository studyStatusLogRepository() {
        return studyStatusLogRepository;
    }

    public com.pessoal.agenda.repository.TaskSessionRepository taskSessionRepository() {
        return taskSessionRepository;
    }

    public com.pessoal.agenda.repository.GoogleTasksMappingRepository googleTasksMappingRepository() {
        return googleTasksMappingRepository;
    }

    public StudyAttendanceService studyAttendanceService() {
        return studyAttendanceService;
    }

    public InsightsService insightsService() {
        return insightsService;
    }

    public NaturalLanguageTaskParser naturalLanguageTaskParser() {
        return naturalLanguageTaskParser;
    }

    public TaskPrioritizer taskPrioritizer() {
        return taskPrioritizer;
    }
}
