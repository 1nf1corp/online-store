package opensource.onlinestore.model.entity;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Created by maks(avto12@i.ua) on 27.01.2016.
 */
@Entity
@Table(name = "messages")
public class MessageEntity extends BaseEntity{

    @ManyToOne
    UserEntity user;
}
