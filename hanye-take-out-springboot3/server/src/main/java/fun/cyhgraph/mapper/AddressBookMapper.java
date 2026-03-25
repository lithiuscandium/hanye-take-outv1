package fun.cyhgraph.mapper;

import fun.cyhgraph.entity.AddressBook;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AddressBookMapper {

    @Insert("insert into address_book" +
            "        (user_id, consignee, phone, gender, province_code, province_name, city_code, city_name, district_code," +
            "         district_name, detail, label, is_default, campus_node_id, is_public)" +
            "        values (#{userId}, #{consignee}, #{phone}, #{gender}, #{provinceCode}, #{provinceName}, #{cityCode}, #{cityName}," +
            "                #{districtCode}, #{districtName}, #{detail}, #{label}, #{isDefault}, #{campusNodeId}, #{isPublic})")
    void insert(AddressBook addressBook);

    List<AddressBook> getUserAddress(AddressBook addressBook);

    void udpate(AddressBook addressBook);

    @Update("update address_book set is_default = #{isDefault} where user_id = #{userId}")
    void updateIsDefaultByUserId(AddressBook addressBook);

    @Select("select * from address_book where id = #{id}")
    AddressBook getById(Integer id);

    @Select("select * from address_book where user_id = #{userId} and campus_node_id = #{campusNodeId} and is_public = 0 order by id desc limit 1")
    AddressBook getByUserAndCampusNode(@Param("userId") Integer userId, @Param("campusNodeId") Integer campusNodeId);

    @Delete("delete from address_book where id = #{id}")
    void delete(Integer id);
}
