package com.polyauth.service.port.system;

import com.polyauth.infrastructure.persistence.structured.entity.IamSystem;

import java.util.List;

/**
 * 子系统登记用例契约。
 */
public interface SystemUseCase {

    IamSystem registerSystem(String systemCode, String systemName, String description);

    List<IamSystem> listSystems();

    IamSystem getSystem(Long systemId);
}
