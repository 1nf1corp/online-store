package opensource.onlinestore.service;

import opensource.onlinestore.Utils.Exceptions.NotFoundException;
import opensource.onlinestore.model.dto.UserDTO;
import opensource.onlinestore.model.entity.UserEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * Created by maks(avto12@i.ua) on 27.01.2016.
 */
public interface UserService {

    UserEntity registerNewUser(UserDTO userDTO);

    String authenticateUserAndGetToken(UserEntity user);

    UserEntity save(UserEntity user);

    void delete(Long id) throws NotFoundException;

    UserEntity get(Long id) throws NotFoundException;

    UserEntity getByEmail(String email) throws NotFoundException;

    Collection<UserEntity> getAll();

    void update(UserEntity user);

    UserEntity getWithOrders(int id);
}
