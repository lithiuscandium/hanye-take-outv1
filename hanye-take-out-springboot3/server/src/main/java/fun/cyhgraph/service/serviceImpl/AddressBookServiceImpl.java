package fun.cyhgraph.service.serviceImpl;

import fun.cyhgraph.context.BaseContext;
import fun.cyhgraph.entity.AddressBook;
import fun.cyhgraph.exception.AddressBookBusinessException;
import fun.cyhgraph.mapper.AddressBookMapper;
import fun.cyhgraph.service.AddressBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AddressBookServiceImpl implements AddressBookService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 新增地址
     * @param addressBook
     */
    public void addAddress(AddressBook addressBook) {
        // 要先知道是哪个用户要新增地址，并且刚开始无法设置默认地址，需要在其他前端页面设置
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        addressBook.setIsPublic(0);
        addressBook.setCampusNodeId(null);
        addressBookMapper.insert(addressBook);
    }

    /**
     * 条件查询用户地址
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {
        return addressBookMapper.getUserAddress(addressBook);
    }

    /**
     * 修改地址
     * @param addressBook
     */
    public void updateAddress(AddressBook addressBook) {
        if (addressBook == null || addressBook.getId() == null) {
            throw new AddressBookBusinessException("地址参数错误");
        }
        AddressBook db = addressBookMapper.getById(addressBook.getId());
        if (db == null) {
            throw new AddressBookBusinessException("地址不存在");
        }
        Integer currentUserId = BaseContext.getCurrentId();
        if (db.getIsPublic() != null && db.getIsPublic() == 1) {
            throw new AddressBookBusinessException("公共地址不可编辑");
        }
        if (!currentUserId.equals(db.getUserId())) {
            throw new AddressBookBusinessException("无权限修改该地址");
        }
        addressBookMapper.udpate(addressBook);
    }

    /**
     * 根据id查询地址
     * @param id
     * @return
     */
    public AddressBook getById(Integer id) {
        AddressBook db = addressBookMapper.getById(id);
        if (db == null) {
            return null;
        }
        Integer currentUserId = BaseContext.getCurrentId();
        boolean isPublic = db.getIsPublic() != null && db.getIsPublic() == 1;
        boolean isOwn = db.getUserId() != null && db.getUserId().equals(currentUserId);
        if (!isPublic && !isOwn) {
            throw new AddressBookBusinessException("地址不存在");
        }
        return db;
    }

    /**
     * 设置默认地址
     * @param addressBook
     */
    public void setDefault(AddressBook addressBook) {
        if (addressBook == null || addressBook.getId() == null) {
            throw new AddressBookBusinessException("地址参数错误");
        }
        Integer currentUserId = BaseContext.getCurrentId();
        AddressBook db = addressBookMapper.getById(addressBook.getId());
        if (db == null) {
            throw new AddressBookBusinessException("地址不存在");
        }
        // 1、先把当前用户所有地址都设置成非默认地址
        addressBook.setIsDefault(0);
        addressBook.setUserId(currentUserId);
        addressBookMapper.updateIsDefaultByUserId(addressBook);
        // 2、再把当前地址设置成默认地址
        boolean isPublic = db.getIsPublic() != null && db.getIsPublic() == 1;
        if (isPublic) {
            // 公共地址被设为默认时，为当前用户复制一份私有地址，避免改动全局数据
            AddressBook exists = db.getCampusNodeId() == null ? null :
                    addressBookMapper.getByUserAndCampusNode(currentUserId, db.getCampusNodeId());
            if (exists != null) {
                AddressBook update = AddressBook.builder()
                        .id(exists.getId())
                        .isDefault(1)
                        .build();
                addressBookMapper.udpate(update);
            } else {
                AddressBook copy = AddressBook.builder()
                        .userId(currentUserId)
                        .consignee(db.getConsignee())
                        .phone(db.getPhone())
                        .gender(db.getGender())
                        .provinceCode(db.getProvinceCode())
                        .provinceName(db.getProvinceName())
                        .cityCode(db.getCityCode())
                        .cityName(db.getCityName())
                        .districtCode(db.getDistrictCode())
                        .districtName(db.getDistrictName())
                        .detail(db.getDetail())
                        .label(db.getLabel())
                        .isDefault(1)
                        .campusNodeId(db.getCampusNodeId())
                        .isPublic(0)
                        .build();
                addressBookMapper.insert(copy);
            }
        } else {
            if (db.getUserId() == null || !db.getUserId().equals(currentUserId)) {
                throw new AddressBookBusinessException("无权限设置该默认地址");
            }
            AddressBook update = AddressBook.builder()
                    .id(db.getId())
                    .isDefault(1)
                    .build();
            addressBookMapper.udpate(update);
        }
    }

    /**
     * 根据id删除地址
     * @param id
     */
    public void deleteById(Integer id) {
        if (id == null) {
            throw new AddressBookBusinessException("地址参数错误");
        }
        AddressBook db = addressBookMapper.getById(id);
        if (db == null) {
            return;
        }
        Integer currentUserId = BaseContext.getCurrentId();
        if (db.getIsPublic() != null && db.getIsPublic() == 1) {
            throw new AddressBookBusinessException("公共地址不可删除");
        }
        if (db.getUserId() == null || !db.getUserId().equals(currentUserId)) {
            throw new AddressBookBusinessException("无权限删除该地址");
        }
        addressBookMapper.delete(id);
    }

}
