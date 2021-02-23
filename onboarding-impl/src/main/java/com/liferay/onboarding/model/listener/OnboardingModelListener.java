package com.liferay.onboarding.model.listener;

import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecord;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecordVersion;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordLocalService;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.journal.util.comparator.FeedIDComparator;
import com.liferay.petra.string.StringPool;
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
import com.liferay.portal.kernel.util.LocaleUtil;
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
      _log.info(FormData);

      long companyId = formRecord.getCompanyId();
      long userID =_counterLocalService.increment();
      long adminUserId =  _companyLocalService
              .getCompany(formRecord.getCompanyId())
              .getDefaultUser()
              .getUserId();
      String firstName = FormData.get(getFieldID( _config.firstName(),currentFormID)).get(0);
      String middleName =FormData.get(getFieldID( _config.middleName(),currentFormID)).get(0);
      String lastName = FormData.get(getFieldID( _config.lastName(),currentFormID)).get(0);
      String emailAddress = FormData.get(getFieldID( _config.emailAddressField(),currentFormID)).get(0);
      User newUser = _userLocalService.addUser(adminUserId,
              companyId,
              false,
              "liferay",
              "liferay",
              false,
              "User_"+userID,
              emailAddress,
              0L,
              StringPool.BLANK,
              LocaleUtil.getDefault(),
              firstName,
              StringPool.BLANK,
              lastName,
              0L,
              0L,
              true,
              Calendar.JANUARY,
              1,
              1970,
              StringPool.BLANK,
              new long[0],
              new long[0],
              new long[0],
              new long[0],
              false,
              null);
      Thread.sleep(100);
      newUser.setStatus(WorkflowConstants.STATUS_INACTIVE);
      newUser.setAgreedToTermsOfUse(true);
      newUser.setNew(true);
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
      long currentFormID = formRecord.getFormInstanceId();
      long RoleID = getMonitoredFormRoles(currentFormID);
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
  @Reference
  private CounterLocalService _counterLocalService;
}
