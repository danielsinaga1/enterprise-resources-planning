package id.enterpriseapps.service.lookup;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import id.enterpriseapps.constant.ErrorCodeConstant;
import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.entity.LookupEntity;
import id.enterpriseapps.repository.LookupRepository;
import id.enterpriseapps.response.Response;

@Service
public class LookupService {

  @Autowired
  private LookupRepository lookupRepository;

  @Cacheable(value = "lookups", key = "#type")
  public Response getAllLookup(String type) {
    try {
      List<LookupEntity> data = lookupRepository.findByType(type);
      return Response.builder()
          .status(StatusConstant.SUCCESS)
          .httpStatus(HttpStatus.OK)
          .data(data)
          .build();
    } catch (Exception e) {
      return Response.builder()
          .status(StatusConstant.FAILED)
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorCode(ErrorCodeConstant.Service_or_Database_Exception)
          .errorMessage(ErrorCodeConstant.error_message_general)
          .build();
    }
  }
}

