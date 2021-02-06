package com.liferay.onboarding.model.listener;

import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecord;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecordVersion;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordLocalService;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.journal.util.comparator.FeedIDComparator;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

@Component(
  immediate = true,
  service = ModelListener.class,
  configurationPid = OnboardingModelListenerConfiguration.PID
)
public class OnboardingModelListener
  extends BaseModelListener<DDMFormInstanceRecord> {

  @Reference
  private DDMFormInstanceRecordLocalService _ddmFormInstanceRecordLocalService;
  @Override
  public void onAfterCreate(DDMFormInstanceRecord model)
    throws ModelListenerException {}
  @Override
  public void onAfterUpdate(DDMFormInstanceRecord formRecord)
    throws ModelListenerException {
    try {
      String formsIds = _config.formId();
      if (
          isFormMonitored(formRecord.getFormInstanceId()) && formRecord.getStatus() == 1
      ) {
        if (_config.CreateUser()) {
          addInactiveUser(formRecord);
        }
      }
      if (
          isFormMonitored(formRecord.getFormInstanceId()) && formRecord.getStatus() == 0
      ) {
        if (_config.CreateUser()) {
          activateUser(formRecord);
          if (_config.AddToRole()) {
            assignRoleToCreatedUser(formRecord);
          }
        }
        if (_config.AddToRole() && !_config.CreateUser()) {
          _log.info("assigning role to user");
          assignRoleToCreatorUser(formRecord);
        }
      }
    } catch (PortalException e) {
      _log.info("error at first level" + e.getMessage());
    }
  }
  public boolean isFormMonitored(long currentFormID)
  {
    if(_config.formId().contains(";"))
    {
      List<String> formsIDs = Arrays.asList(_config.formId().split(";"));
      return formsIDs.contains(Long.toString(currentFormID));
    }else
    {
      return Long.toString(currentFormID) == _config.formId();
    }

  }
  public int indexOfMonitoredForm(long currentFormID)
  {
      List<String> formsIDs = Arrays.asList(_config.formId().split(";"));
      return formsIDs.indexOf(Long.toString(currentFormID));
  }
  public long getMonitoredFormRoles(long currentFormID)
  {
    if(_config.roleID().contains(";"))
    {
      int indexOfMonitoredForm = indexOfMonitoredForm(currentFormID);
      List<String> formsRoles = Arrays.asList(_config.roleID().split(";"));
      return Long.valueOf(formsRoles.get(indexOfMonitoredForm));
    }else
    {
      return Long.valueOf(_config.roleID());
    }
  }
  public long getMonitoredFormSite(long currentFormID)
  {
    if(_config.siteID().contains(";"))
    {
      int indexOfMonitoredForm = indexOfMonitoredForm(currentFormID);
      List<String> formsSites = Arrays.asList(_config.siteID().split(";"));
      return Long.valueOf(formsSites.get(indexOfMonitoredForm));
    }else
    {
      return Long.valueOf(_config.siteID());
    }
  }
  /**
   * Add a user with the status 'inactive' when a form to create an account is submitted for
   * publication.
   * @param formRecord a form submission
   */
  private void addInactiveUser(DDMFormInstanceRecord formRecord) {
    try {
      HashMap<String, ArrayList<String>> FormData = new HashMap<String, ArrayList<String>>();
      DDMFormValues ddmFormValues = _ddmFormInstanceRecordLocalService.getDDMFormValues(
        formRecord.getStorageId(),
        formRecord.getDDMFormValues().getDDMForm()
      );
      long currentFormID = formRecord.getFormInstanceId();
      _log.info(getFieldID( _config.firstName(),currentFormID));
      CollectFormValues(ddmFormValues.getDDMFormFieldValuesMap(), FormData);
      //Liferay 7.3 form fields have an auto-generated name. Hence the hardcoded horrible fieldnames below
      _log.info(FormData);
      User newUser = _userLocalService.addUser(
        _companyLocalService
          .getCompany(formRecord.getCompanyId())
          .getDefaultUser()
          .getUserId(),
        formRecord.getCompanyId(),
        false,
        "liferay$",
        "liferay$",
        true,
        null,
        FormData.get(getFieldID( _config.emailAddressField(),currentFormID)).get(0),
        -1L,
        null,
        formRecord.getDDMFormValues().getDefaultLocale(),
        FormData.get(getFieldID( _config.firstName(),currentFormID)).get(0),
        FormData.get(getFieldID( _config.middleName(),currentFormID)).get(0),
        FormData.get(getFieldID( _config.lastName(),currentFormID)).get(0),
        -1L,
        -1L,
        false,
        1,
        1,
        1970,
        null,
        null,
        null,
        null,
        null,
        false,
        null
      );
      newUser.setStatus(WorkflowConstants.STATUS_INACTIVE);
      _userLocalService.updateUser(newUser);
    } catch (Exception e) {
      _log.error("error at addInactiveUser Method", e);
    }
  }
  String getFieldID(String FieldConfigurationValue,long currentFormID)
  {
    if(FieldConfigurationValue.contains(";"))
    {
      int indexOfMonitoredForm = indexOfMonitoredForm(currentFormID);
      List<String> FieldIDs = Arrays.asList(FieldConfigurationValue.split(";"));
      return FieldIDs.get(indexOfMonitoredForm);
    }
    else
    {
      return FieldConfigurationValue;
    }
  }
  /**
   * Update an inactive user with the status 'active' if the form submission has been approved.
   * @param formRecord a form submission
   */
  private void activateUser(DDMFormInstanceRecord formRecord) {
    try {
      HashMap<String, ArrayList<String>> FormData = new HashMap<String, ArrayList<String>>();
      DDMFormValues ddmFormValues = _ddmFormInstanceRecordLocalService.getDDMFormValues(
        formRecord.getStorageId(),
        formRecord.getFormInstance().getDDMForm()
      );
      CollectFormValues(ddmFormValues.getDDMFormFieldValuesMap(), FormData);
      User user = _userLocalService.getUserByEmailAddress(
        formRecord.getCompanyId(),
          FormData.get(getFieldID(_config.emailAddressField(),formRecord.getFormInstanceId())).get(0)
      );
      user.setStatus(WorkflowConstants.STATUS_APPROVED);
      _userLocalService.updateUser(user);
    } catch (Exception e) {
      _log.error("error at activateUser Method", e);
    }
  }
  /*
	Assign user to the selected role in the model listener configurations, this method will 
	be invoked if the user has been created by the Model Listener as a new user.
  */
  private void assignRoleToCreatedUser(DDMFormInstanceRecord formRecord) {
    try {
      User Creator = _userLocalService.getUserById(formRecord.getUserId());
      long currentFormID = formRecord.getFormInstanceId();
      long RoleID = getMonitoredFormRoles(currentFormID);
      HashMap<String, ArrayList<String>> FormData = new HashMap<String, ArrayList<String>>();
      DDMFormValues ddmFormValues = _ddmFormInstanceRecordLocalService.getDDMFormValues(
        formRecord.getStorageId(),
        formRecord.getFormInstance().getDDMForm()
      );
      CollectFormValues(ddmFormValues.getDDMFormFieldValuesMap(), FormData);
      ////_log.debug("Entering activateUser() method");
      User user = _userLocalService.getUserByEmailAddress(
        formRecord.getCompanyId(),
        FormData.get(getFieldID(_config.emailAddressField(),formRecord.getFormInstanceId())).get(0)
      );
      _userLocalService.addRoleUser(RoleID, user);
      _userLocalService.addGroupUser(getMonitoredFormSite(formRecord.getFormInstanceId()),user);
    } catch (Exception e) {
      _log.error("error at assignRoleToCreatedUser Method", e);
    }
  }

  /*
	Assign user to the selected role in the model listener configurations, this method will 
	be invoked if the user has not been created by the Model Listener and will use the creator
	user to assign the role to.
	 */
  private void assignRoleToCreatorUser(DDMFormInstanceRecord formRecord) {
    try {
      User Creator = _userLocalService.getUserById(formRecord.getUserId());
      long currentFormID = formRecord.getFormInstanceId();
      long RoleID = getMonitoredFormRoles(currentFormID);
      _log.info(RoleID);
      _log.info(
        "assigning role " + _config.roleID() + " to user" + Creator.getUserId()
      );
      _userLocalService.addRoleUser(RoleID, Creator);
      _userLocalService.addGroupUser(getMonitoredFormSite(formRecord.getFormInstanceId()),Creator);
    } catch (Exception e) {
      _log.error("error at assignRoleToCreatorUser Method", e);
    }
  }
  private static final Log _log = LogFactoryUtil.getLog(
    OnboardingModelListener.class
  );
  private void CollectFormValues(
    Map<String, List<DDMFormFieldValue>> formFieldValueMap,
    HashMap<String, ArrayList<String>> FormData
  ) {
    for (Map.Entry<String, List<DDMFormFieldValue>> entry : formFieldValueMap.entrySet()) {
      for (DDMFormFieldValue formFieldValue : entry.getValue()) {
        try {
          Map<String, List<DDMFormFieldValue>> nested = formFieldValue.getNestedDDMFormFieldValuesMap();
          if (!nested.isEmpty()) {
            CollectFormValues(nested, FormData);
          } else {
            String Value = "Null";
            String FieldName = formFieldValue.getName();
            Value =
              formFieldValue
                .getValue()
                .getString(formFieldValue.getValue().getDefaultLocale());
            if (FormData.get(FieldName) == null) {
              ArrayList<String> data = new ArrayList<String>();
              data.add(Value);
              FormData.put(FieldName, data);
            } else {
              FormData.get(FieldName).add(Value);
            }
          }
        } catch (Exception ex) {
          //_log.error("error on "  ,ex);
        }
      }
    }
  }
  @Activate
  @Modified
  public void activate(Map<String, String> properties) {
    try {
      _config =
        ConfigurableUtil.createConfigurable(
          OnboardingModelListenerConfiguration.class,
          properties
        );
    } catch (Exception e) {
      _log.error(
        "error while activating Onboarding Model Listener, please provide a valid configurations"
      );
    }
  }

  @Reference
  private CompanyLocalService _companyLocalService;

  private volatile OnboardingModelListenerConfiguration _config;

  @Reference
  private UserLocalService _userLocalService;
}
