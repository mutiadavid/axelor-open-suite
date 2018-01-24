/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.hr.web.timesheet;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.axelor.apps.hr.service.HRMenuValidateService;
import com.axelor.apps.hr.service.employee.EmployeeService;
import com.axelor.apps.report.engine.ReportSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.message.MessageServiceBaseImpl;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.ExtraHours;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.report.IReport;
import com.axelor.apps.hr.service.HRMenuTagService;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.apps.hr.service.user.UserHrService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class TimesheetController {
	
	private final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );
	
	@Inject
	private Provider<HRMenuTagService> hrMenuTagServiceProvider;
	@Inject
	private Provider<TimesheetService> timesheetServiceProvider;
	@Inject
	private Provider<TimesheetRepository> timesheetRepositoryProvider;
	@Inject
	private Provider<ProductRepository> productRepoProvider;
	@Inject
	private Provider<ProjectRepository> projectRepoProvider;
	@Inject
	private Provider<UserHrService> userHrservice;
	
	public void getTimeFromTask(ActionRequest request, ActionResponse response){
		Timesheet timesheet = request.getContext().asType(Timesheet.class);
		timesheet = timesheetRepositoryProvider.get().find(timesheet.getId());
		timesheetServiceProvider.get().getTimeFromTask(timesheet);
		response.setReload(true);
	}

	@SuppressWarnings("unchecked")
	public void generateLines(ActionRequest request, ActionResponse response) throws AxelorException{
		Timesheet timesheet = request.getContext().asType(Timesheet.class);
		Context context = request.getContext();
		
		LocalDate fromGenerationDate = null;
		if(context.get("fromGenerationDate") != null)
			fromGenerationDate = LocalDate.parse(context.get("fromGenerationDate").toString(), DateTimeFormatter.ISO_DATE);
		LocalDate toGenerationDate = null;
		if(context.get("toGenerationDate") != null)
			toGenerationDate = LocalDate.parse(context.get("toGenerationDate").toString(), DateTimeFormatter.ISO_DATE);
		BigDecimal logTime = BigDecimal.ZERO;
		if(context.get("logTime") != null)
			logTime = new BigDecimal(context.get("logTime").toString());
		
		Map<String, Object> projectContext = (Map<String, Object>) context.get("project");
		Project project = null;
		if (projectContext != null) {
            project = projectRepoProvider.get().find(((Integer) projectContext.get("id")).longValue());
        }
		
		Map<String, Object> productContext = (Map<String, Object>) context.get("product");
		Product product = null;
		if(productContext != null)
			product = productRepoProvider.get().find(((Integer) productContext.get("id")).longValue());

		if (context.get("showActivity") == null || !(Boolean) context.get("showActivity")) {
			product = userHrservice.get().getTimesheetProduct(timesheet.getUser());
		}
		
		timesheet = timesheetServiceProvider.get().generateLines(timesheet, fromGenerationDate, toGenerationDate, logTime, project, product);
		response.setValue("timesheetLineList",timesheet.getTimesheetLineList());
	}

	public void editTimesheet(ActionRequest request, ActionResponse response){
		List<Timesheet> timesheetList = Beans.get(TimesheetRepository.class).all().filter("self.user = ?1 AND self.company = ?2 AND self.statusSelect = 1",AuthUtils.getUser(),AuthUtils.getUser().getActiveCompany()).fetch();
		if(timesheetList.isEmpty()){
			response.setView(ActionView
									.define(I18n.get("Timesheet"))
									.model(Timesheet.class.getName())
									.add("form", "timesheet-form")
									.map());
		}
		else if(timesheetList.size() == 1){
			response.setView(ActionView
					.define(I18n.get("Timesheet"))
					.model(Timesheet.class.getName())
					.add("form", "timesheet-form")
					.param("forceEdit", "true")
					.context("_showRecord", String.valueOf(timesheetList.get(0).getId())).map());
		}
		else{
			response.setView(ActionView
					.define(I18n.get("Timesheet"))
					.model(Wizard.class.getName())
					.add("form", "popup-timesheet-form")
					.param("forceEdit", "true")
					.param("popup", "true")
					.param("show-toolbar", "false")
					.param("show-confirm", "false")
					.param("forceEdit", "true")
			  		.param("popup-save", "false")
					.map());
		}
	}

	public void validateTimesheet(ActionRequest request, ActionResponse response){
		
		User user = AuthUtils.getUser();
		Employee employee = user.getEmployee();
		
		ActionViewBuilder actionView = ActionView.define(I18n.get("Timesheets to Validate"))
				   .model(Timesheet.class.getName())
				   .add("grid","timesheet-validate-grid")
				   .add("form","timesheet-form")
				   .context("todayDate", Beans.get(AppBaseService.class).getTodayDate());

		Beans.get(HRMenuValidateService.class).createValidateDomain(user, employee, actionView);

		response.setView(actionView.map());
	}

	public void validateTimesheetLine(ActionRequest request, ActionResponse response){

		User user = AuthUtils.getUser();
		Employee employee = user.getEmployee();

		ActionViewBuilder actionView = ActionView.define(I18n.get("See timesheet lines"))
				   .model(TimesheetLine.class.getName())
				   .add("grid","timesheet-line-grid")
				   .add("form","timesheet-line-form")
				   .context("todayDate", Beans.get(AppBaseService.class).getTodayDate());

		timesheetServiceProvider.get().createValidateDomainTimesheetLine(user, employee, actionView);

		response.setView(actionView.map());
	}
	
	public void editTimesheetSelected(ActionRequest request, ActionResponse response){
		Map timesheetMap = (Map)request.getContext().get("timesheetSelect");
		Timesheet timesheet = Beans.get(TimesheetRepository.class).find(new Long((Integer)timesheetMap.get("id")));
		response.setView(ActionView
				.define("Timesheet")
				.model(Timesheet.class.getName())
				.add("form", "timesheet-form")
				.param("forceEdit", "true")
				.domain("self.id = "+timesheetMap.get("id"))
				.context("_showRecord", String.valueOf(timesheet.getId())).map());
	}

	public void historicTimesheet(ActionRequest request, ActionResponse response){
		
		User user = AuthUtils.getUser();
		Employee employee = user.getEmployee();
		
		ActionViewBuilder actionView = ActionView.define(I18n.get("Historic colleague Timesheets"))
				   .model(Timesheet.class.getName())
				   .add("grid","timesheet-grid")
				   .add("form","timesheet-form");

		actionView.domain("self.company = :_activeCompany AND (self.statusSelect = 3 OR self.statusSelect = 4)")
		.context("_activeCompany", user.getActiveCompany());
	
		if(employee == null || !employee.getHrManager())  {
			actionView.domain(actionView.get().getDomain() + " AND self.user.employee.manager = :_user")
			.context("_user", user);
		}
		
		response.setView(actionView.map());
		
	}
	
public void historicTimesheetLine(ActionRequest request, ActionResponse response){

		User user = AuthUtils.getUser();
		Employee employee = user.getEmployee();

		ActionViewBuilder actionView = ActionView.define(I18n.get("See timesheet lines"))
				   .model(TimesheetLine.class.getName())
				   .add("grid","timesheet-line-grid")
				   .add("form","timesheet-line-form");

		actionView.domain("self.timesheet.company = :_activeCompany AND (self.timesheet.statusSelect = 3 OR self.timesheet.statusSelect = 4)")
		.context("_activeCompany", user.getActiveCompany());

		if(employee == null || !employee.getHrManager())  {
			actionView.domain(actionView.get().getDomain() + " AND self.timesheet.user.employee.manager = :_user")
			.context("_user", user);
		}

		response.setView(actionView.map());

	}

	public void showSubordinateTimesheets(ActionRequest request, ActionResponse response){
		
		User user = AuthUtils.getUser();
		Company activeCompany = user.getActiveCompany();
		
		ActionViewBuilder actionView = ActionView.define(I18n.get("Timesheets to be Validated by your subordinates"))
				   .model(Timesheet.class.getName())
				   .add("grid","timesheet-grid")
				   .add("form","timesheet-form");
		
		String domain = "self.user.employee.manager.employee.manager = :_user AND self.company = :_activeCompany AND self.statusSelect = 2";
		
		long nbTimesheets =  Query.of(ExtraHours.class).filter(domain).bind("_user", user).bind("_activeCompany", activeCompany).count();
		
		if(nbTimesheets == 0)  {
			response.setNotify(I18n.get("No timesheet to be validated by your subordinates"));
		}
		else  {
			response.setView(actionView.domain(domain).context("_user", user).context("_activeCompany", activeCompany).map());
		}
		
	}
	
	public void cancel(ActionRequest request, ActionResponse response)  {
		try {
			Timesheet timesheet = request.getContext().asType(Timesheet.class);
			timesheet = timesheetRepositoryProvider.get().find(timesheet.getId());
			TimesheetService timesheetService = timesheetServiceProvider.get();

			timesheetService.cancel(timesheet);

			Message message = timesheetService.sendCancellationEmail(timesheet);
			if (message != null && message.getStatusSelect() == MessageRepository.STATUS_SENT) {
				response.setFlash(String.format(I18n.get("Email sent to %s"), Beans.get(MessageServiceBaseImpl.class).getToRecipients(message)));
			}
		} catch(Exception e) {
			TraceBackService.trace(response, e);
		} finally {
			response.setReload(true);
		}
	}
	
	//action called when confirming a timesheet. Changing status + Sending mail to Manager
	public void confirm(ActionRequest request, ActionResponse response) throws AxelorException{

		try{
			Timesheet timesheet = request.getContext().asType(Timesheet.class);
			timesheet = timesheetRepositoryProvider.get().find(timesheet.getId());
			TimesheetService timesheetService = timesheetServiceProvider.get();
			
			timesheetService.confirm(timesheet);

			Message message = timesheetService.sendConfirmationEmail(timesheet);
			if (message != null && message.getStatusSelect() == MessageRepository.STATUS_SENT) {
				response.setFlash(String.format(I18n.get("Email sent to %s"), Beans.get(MessageServiceBaseImpl.class).getToRecipients(message)));
			} 
			
		} catch (Exception e) {
			TraceBackService.trace(response, e);
		} finally {
			response.setReload(true);
		}
	}

    // Continue button
    public void continueBtn(ActionRequest request, ActionResponse response) {
        response.setView(ActionView
                .define(I18n.get("Timesheet"))
                .model(Timesheet.class.getName())
                .add("form", "timesheet-form")
				.add("grid", "timesheet-grid")
				.domain("self.user = :_user")
				.context("_user", AuthUtils.getUser())
                .map());
    }

    // Confirm and continue button
    public void confirmContinue(ActionRequest request, ActionResponse response) throws AxelorException {
        this.confirm(request, response);
        this.continueBtn(request, response);
    }
	
	
	//action called when validating a timesheet. Changing status + Sending mail to Applicant
	public void valid(ActionRequest request, ActionResponse response) throws AxelorException{
		
		try{
			Timesheet timesheet = request.getContext().asType(Timesheet.class);
			timesheet = timesheetRepositoryProvider.get().find(timesheet.getId());
			TimesheetService timesheetService = timesheetServiceProvider.get();

			timesheetService.validate(timesheet);
			computeTimeSpent(request, response);

			Message message = timesheetService.sendValidationEmail(timesheet);
			if (message != null && message.getStatusSelect() == MessageRepository.STATUS_SENT) {
				response.setFlash(String.format(I18n.get("Email sent to %s"), Beans.get(MessageServiceBaseImpl.class).getToRecipients(message)));
			}

		} catch (Exception e) {
			TraceBackService.trace(response, e);
		} finally {
			response.setReload(true);
		}
		
	}
	
	//action called when refusing a timesheet. Changing status + Sending mail to Applicant
	public void refuse(ActionRequest request, ActionResponse response) throws AxelorException{
		
		try {
			Timesheet timesheet = request.getContext().asType(Timesheet.class);
			timesheet = timesheetRepositoryProvider.get().find(timesheet.getId());
			TimesheetService timesheetService = timesheetServiceProvider.get();

			timesheetService.refuse(timesheet);

			Message message = timesheetService.sendRefusalEmail(timesheet);
			if (message != null && message.getStatusSelect() == MessageRepository.STATUS_SENT) {
				response.setFlash(String.format(I18n.get("Email sent to %s"), Beans.get(MessageServiceBaseImpl.class).getToRecipients(message)));
			} 
			
		} catch (Exception e) {
			TraceBackService.trace(response, e);
		} finally {
			response.setReload(true);
		}
	}
	
	
	public void computeTimeSpent(ActionRequest request, ActionResponse response) {
		Timesheet timesheet = request.getContext().asType(Timesheet.class);
		timesheet = Beans.get(TimesheetRepository.class).find(timesheet.getId());
		if (timesheet.getTimesheetLineList() != null && !timesheet.getTimesheetLineList().isEmpty()) {
			timesheetServiceProvider.get().computeTimeSpent(timesheet);
		}
	}
	
	public void setVisibleDuration(ActionRequest request, ActionResponse response){
		Timesheet timesheet = request.getContext().asType(Timesheet.class);
		timesheet = Beans.get(TimesheetRepository.class).find(timesheet.getId());
		
		response.setValue("timesheetLineList", timesheetServiceProvider.get().computeVisibleDuration(timesheet));
	}
	
	/* Count Tags displayed on the menu items */
	public String timesheetValidateMenuTag()  {
		
		return hrMenuTagServiceProvider.get().countRecordsTag(Timesheet.class, TimesheetRepository.STATUS_CONFIRMED);
	}
	
	public void printTimesheet(ActionRequest request, ActionResponse response) throws AxelorException {
		
		Timesheet timesheet = request.getContext().asType(Timesheet.class);

		String name = I18n.get("Timesheet") + " " + timesheet.getFullName()
												.replace("/", "-");
		
		String fileLink = ReportFactory.createReport(IReport.TIMESHEET, name)
				.addParam("TimesheetId", timesheet.getId())
				.addParam("Locale", ReportSettings.getPrintingLocale(null))
				.toAttach(timesheet)
				.generate()
				.getFileLink();

		logger.debug("Printing "+name);
	
		response.setView(ActionView
				.define(name)
				.add("html", fileLink).map());	
	}

	public void setShowActivity(ActionRequest request, ActionResponse response) {

		Timesheet timesheet = request.getContext().asType(Timesheet.class);

		boolean showActivity = true;

		User user = timesheet.getUser();
		if (user != null) {
			Company company = user.getActiveCompany();
			if (company != null && company.getHrConfig() != null) {
				showActivity = !company.getHrConfig().getUseUniqueProductForTimesheet();
			}
		}

		response.setValue("$showActivity", showActivity);
	}

	public void openTimesheetEditor(ActionRequest request, ActionResponse response) {

		Context context = request.getContext();

		String url = "hr/timesheet?timesheetId=" + context.get("id") + "&showActivity=" + context.get("showActivity");

		response.setView(ActionView
				.define(I18n.get("Timesheet lines"))
				.add("html", url).map());

	}


	public void timesheetPeriodTotalController(ActionRequest request, ActionResponse response) {
		Timesheet timesheet = request.getContext().asType(Timesheet.class);
		User user = timesheet.getUser();

		BigDecimal periodTotal = timesheetServiceProvider.get().computePeriodTotal(timesheet);

		response.setAttr("periodTotal","value",periodTotal);
		response.setAttr("$periodTotalConvert","hidden",false);
		response.setAttr("$periodTotalConvert","value",Beans.get(EmployeeService.class).getUserDuration(periodTotal,user,false));
		response.setAttr("$periodTotalConvert","title",timesheetServiceProvider.get().getPeriodTotalConvertTitleByUserPref(user));
	}
}
